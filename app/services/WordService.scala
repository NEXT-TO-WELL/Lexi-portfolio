package services
import models.db.{TagModel, Word, WordTagModel}

import javax.inject._
import scala.concurrent.{ExecutionContext, Future}

import repositories._
import models._
import utils.Validation
import play.api.Logger
import shared.api.ErrorResponse
import models.ApiModelConversions
import shared.api.{ExampleViewValue, MeaningViewValue}

@Singleton
class WordService @Inject() (
    wordRepo: WordRepository,
    tagRepo: TagModelRepository,
    wordTagRepo: WordTagModelRepository,
    meaningRepo: MeaningModelRepository,
    exampleRepo: ExampleModelRepository,
    userRepo: UserRepository,
    favoriteRepo: FavoriteModelRepository,
    reactionRepo: ReactionModelRepository,
    followRepo: FollowModelRepository,
    notificationService: NotificationService
)(implicit ec: ExecutionContext) {

  private val logger = Logger(this.getClass)
  private val RichTextMaxLength = 1000

  // WordService.scala
  def findWordsByKeyword(
      keyword: Option[String],
      wordId: Option[Long],
      tagId: Option[Long],
      userIdOpt: Option[String]
  ): Future[Seq[SearchViewValue]] = {

    // --- 1. IDが一つでもあれば、IDベースの検索を優先 ---
    if (wordId.isDefined || tagId.isDefined) {
      wordTagRepo.searchBySpecificIds(wordId, tagId).map { rows =>
        rows.map { case ((wt, word), tag) =>
          SearchViewValue(
            wordId = wt.wordId,
            tagId = wt.tagId,
            wordMain = word.wordMain,
            tagName = tag.tagName,
            wordReading = wt.wordReading
          )
        }
      }
    } else {
      // --- 2. IDが全くない場合のみ、文字列によるキーワード検索 ---
      val keywords = keyword.getOrElse("").trim.split("[\\s\\u3000]+").filter(_.nonEmpty).toList

      keywords match {
        case first :: tail if (first.startsWith("#") || first.startsWith("＃")) && first.length > 1 =>
          val firstTag = first.substring(1)
          wordTagRepo.findByTagNamesAndKeywords(firstTag, tail).map { rows =>
            rows.map { case ((wt, word), tag) =>
              SearchViewValue(wt.wordId, wt.tagId, word.wordMain, tag.tagName, wt.wordReading)
            }
          }

        case _ if keywords.nonEmpty =>
          wordTagRepo.findWordsByMultipleKeywords(keywords).map { rows =>
            rows.map { case ((wt, word), tag) =>
              SearchViewValue(wt.wordId, wt.tagId, word.wordMain, tag.tagName, wt.wordReading)
            }
          }

        case _ => Future.successful(Nil)
      }
    }
  }

  // UserService.scala

  // WordService.scala

  def getMeaningsWithStatus(
      wordId: Long,
      tagId: Long,
      currentUserIdOpt: Option[String]
  ): Future[Seq[MeaningViewValue]] = {
    for {
      // 1. 意味のリストを取得
      meaningRows <- meaningRepo.findByWordTagId(wordId, tagId)
      meaningIds = meaningRows.map { case (m, _) => m.meaningId.getOrElse(0L) }

      // 2. ログイン中なら各テーブルからリアクション状況を並列取得
      (userGoods, userHelpfuls, userFunnies, userFavorites) <- currentUserIdOpt match {
        case Some(uid) =>
          for {
            // ContentTypeId "1" は Meaning と想定
            g <- reactionRepo.findByContentIds(uid, "1", meaningIds, "good")
            h <- reactionRepo.findByContentIds(uid, "1", meaningIds, "helpful")
            f <- reactionRepo.findByContentIds(uid, "1", meaningIds, "funny")
            fav <- favoriteRepo.findByContentIds(uid, "1", meaningIds)
          } yield (g, h, f, fav)
        case None =>
          Future.successful((Nil, Nil, Nil, Nil))
      }
    } yield {
      // 3. 全てのステータスを MeaningViewValue にマッピング
      meaningRows.map { case (m, userOpt) =>
        val mId = m.meaningId.getOrElse(0L)
        MeaningViewValue(
          meaning = ApiModelConversions.meaning(m),
          userName = userOpt.map(_.userName).getOrElse("unknown"),
          displayId = userOpt.map(_.displayId), // ← 追加
          isGood = userGoods.exists(_.contentId == mId),
          isHelpful = userHelpfuls.exists(_.contentId == mId),
          isFunny = userFunnies.exists(_.contentId == mId),
          isFavorite = userFavorites.exists(_.contentId == mId)
        )
      }
    }
  }

  /** ページネーション対応：語釈を取得 */
  def getMeaningsWithStatusPaginated(
      wordId: Long,
      tagId: Long,
      currentUserIdOpt: Option[String],
      lastId: Option[Long],
      limit: Int = 10
  ): Future[Seq[MeaningViewValue]] = {
    for {
      // 1. ページネーション対応の語釈取得
      meaningRows <- meaningRepo.findByWordTagIdPaginated(wordId, tagId, lastId, limit)
      meaningIds = meaningRows.map { case (m, _) => m.meaningId.getOrElse(0L) }

      // 2. ログイン中なら各テーブルからリアクション状況を並列取得
      (userGoods, userHelpfuls, userFunnies, userFavorites) <- currentUserIdOpt match {
        case Some(uid) =>
          for {
            g <- reactionRepo.findByContentIds(uid, "1", meaningIds, "good")
            h <- reactionRepo.findByContentIds(uid, "1", meaningIds, "helpful")
            f <- reactionRepo.findByContentIds(uid, "1", meaningIds, "funny")
            fav <- favoriteRepo.findByContentIds(uid, "1", meaningIds)
          } yield (g, h, f, fav)
        case None =>
          Future.successful((Nil, Nil, Nil, Nil))
      }
    } yield {
      // 3. MeaningViewValue にマッピング
      meaningRows.map { case (m, userOpt) =>
        val mId = m.meaningId.getOrElse(0L)
        MeaningViewValue(
          meaning = ApiModelConversions.meaning(m),
          userName = userOpt.map(_.userName).getOrElse("unknown"),
          displayId = userOpt.map(_.displayId), // ← 追加
          isGood = userGoods.exists(_.contentId == mId),
          isHelpful = userHelpfuls.exists(_.contentId == mId),
          isFunny = userFunnies.exists(_.contentId == mId),
          isFavorite = userFavorites.exists(_.contentId == mId)
        )
      }
    }
  }

  def getExamplesWithStatus(
      wordId: Long,
      tagId: Long,
      currentUserIdOpt: Option[String]
  ): Future[Seq[ExampleViewValue]] = {
    for {
      // 1. 例文と投稿者情報を取得
      exampleRows <- exampleRepo.findByWordTagId(wordId, tagId)
      exampleIds = exampleRows.map(_._1.exampleId.getOrElse(0L))

      // 2. ログイン中なら、お気に入り・3種のリアクションを並列取得
      (userGoods, userHelpfuls, userFunnies, userFavorites) <- currentUserIdOpt match {
        case Some(uid) =>
          for {
            // ContentTypeId "2" = Example と想定
            g <- reactionRepo.findByContentIds(uid, "2", exampleIds, "good")
            h <- reactionRepo.findByContentIds(uid, "2", exampleIds, "helpful")
            f <- reactionRepo.findByContentIds(uid, "2", exampleIds, "funny")
            fav <- favoriteRepo.findByContentIds(uid, "2", exampleIds)
          } yield (g, h, f, fav)
        case None =>
          Future.successful((Nil, Nil, Nil, Nil))
      }
    } yield {
      // 3. ExampleViewValue へのマッピング
      exampleRows.map { case (e, userOpt) =>
        val eId = e.exampleId.getOrElse(0L)

        ExampleViewValue(
          example = ApiModelConversions.example(e),
          userName = userOpt.map(_.userName).getOrElse("unknown"),
          displayId = userOpt.map(_.displayId), // ← 追加
          isFavorite = userFavorites.exists(_.contentId == eId),
          isGood = userGoods.exists(_.contentId == eId),
          isHelpful = userHelpfuls.exists(_.contentId == eId),
          isFunny = userFunnies.exists(_.contentId == eId)
        )
      }
    }
  }

  /** ページネーション対応：用例を取得 */
  def getExamplesWithStatusPaginated(
      wordId: Long,
      tagId: Long,
      currentUserIdOpt: Option[String],
      lastId: Option[Long],
      limit: Int = 20
  ): Future[Seq[ExampleViewValue]] = {
    for {
      // 1. ページネーション対応の例文取得
      exampleRows <- exampleRepo.findByWordTagIdPaginated(wordId, tagId, lastId, limit)
      exampleIds = exampleRows.map(_._1.exampleId.getOrElse(0L))

      // 2. ログイン中なら、お気に入り・3種のリアクションを並列取得
      (userGoods, userHelpfuls, userFunnies, userFavorites) <- currentUserIdOpt match {
        case Some(uid) =>
          for {
            g <- reactionRepo.findByContentIds(uid, "2", exampleIds, "good")
            h <- reactionRepo.findByContentIds(uid, "2", exampleIds, "helpful")
            f <- reactionRepo.findByContentIds(uid, "2", exampleIds, "funny")
            fav <- favoriteRepo.findByContentIds(uid, "2", exampleIds)
          } yield (g, h, f, fav)
        case None =>
          Future.successful((Nil, Nil, Nil, Nil))
      }
    } yield {
      // 3. ExampleViewValue へのマッピング
      exampleRows.map { case (e, userOpt) =>
        val eId = e.exampleId.getOrElse(0L)

        ExampleViewValue(
          example = ApiModelConversions.example(e),
          userName = userOpt.map(_.userName).getOrElse("unknown"),
          displayId = userOpt.map(_.displayId), // ← 追加
          isFavorite = userFavorites.exists(_.contentId == eId),
          isGood = userGoods.exists(_.contentId == eId),
          isHelpful = userHelpfuls.exists(_.contentId == eId),
          isFunny = userFunnies.exists(_.contentId == eId)
        )
      }
    }
  }

  def findWordTagDetail(wordId: Long, tagId: Long, userIdOpt: Option[String]): Future[Option[WordTagDetailResponse]] = {
    wordTagRepo.findByWordTagId(wordId, tagId).flatMap {
      case Some((wt, word, tag)) =>
        // ログイン中ならフォロー状況のみ確認
        val isFollowedFuture = userIdOpt match {
          case Some(uid) =>
            followRepo.findExisting(uid, tagId, Some(wordId)).map(_.isDefined)
          case None =>
            Future.successful(false)
        }

        isFollowedFuture.map { isFoll =>
          Some(
            WordTagDetailResponse(
              wordId = wt.wordId,
              wordMain = word.wordMain,
              tagId = wt.tagId,
              tagName = tag.tagName,
              wordReading = wt.wordReading,
              isFollowed = isFoll
            )
          )
        }

      case None =>
        Future.successful(None)
    }
  }

  def postWord(
      req: WordTagPostRequest,
      userId: Option[String],
      dateTime: Long
  ): Future[Either[ErrorResponse, WordTagPostResponse]] = {

    // 1. バリデーション
    if (!Validation.notBlank(req.wordMain) || !Validation.notBlank(req.tagName)) {
      Future.successful(Left(ErrorResponse("400", "言葉とタグに空白のみは使用できません")))
    } else if (!Validation.lengthVal(100, req.wordMain) || !Validation.lengthVal(100, req.tagName)) {
      Future.successful(Left(ErrorResponse("400", "言葉とタグはそれぞれ100文字以内で入力してください")))
    } else if (!Validation.isValidReading(req.wordReading)) {
      Future.successful(Left(ErrorResponse("400", "読みはひらがな・全角カタカナと伸ばし棒（ー）のみで入力してください")))
    } else if (req.tagName.equalsIgnoreCase("Lexi")) {
      Future.successful(Left(ErrorResponse("403", "「Lexi」タグはシステム予約のため使用できません")))
    } else if (
      req.meaning.exists(entry => !Validation.lengthVal(RichTextMaxLength, utils.Validation.trimAll(entry.text)))
    ) {
      Future.successful(Left(ErrorResponse("400", s"語釈の説明は${RichTextMaxLength}文字以内で入力してください")))
    } else if (
      req.example.exists(entry => !Validation.lengthVal(RichTextMaxLength, utils.Validation.trimAll(entry.text)))
    ) {
      Future.successful(Left(ErrorResponse("400", s"例文は${RichTextMaxLength}文字以内で入力してください")))
    } else {

      val finalAction = for {
        // 言葉の取得・作成
        wordObj <- wordRepo.findByMain(req.wordMain).flatMap {
          case words if words.nonEmpty => Future.successful(words.head)
          case _ => wordRepo.create(Word(None, req.wordMain)).map(id => Word(Some(id), req.wordMain))
        }

        // タグの検索・作成
        tagObj <- tagRepo.findByName(req.tagName).flatMap {
          case tags if tags.nonEmpty => Future.successful(tags.head)
          case _ => tagRepo.create(TagModel(None, req.tagName)).map(id => TagModel(Some(id), req.tagName))
        }

        wId = wordObj.wordId.get
        tId = tagObj.tagId.get

        // WordTagの組み合わせが存在しない場合のみ作成
        _ <- wordTagRepo.findByWordTagId(wId, tId).flatMap {
          case Some(_) => Future.successful(())
          case None    =>
            val newWt = WordTagModel(wId, tId, userId, dateTime, req.wordReading)
            wordTagRepo.saveWordTag(newWt).map(_ => ())
        }

        // 意味（Meaning）の保存
        _ <- req.meaning
          .map(entry => entry.copy(text = utils.Validation.trimAll(entry.text)))
          .filter(_.text.nonEmpty) match {
          case Some(entry) =>
            meaningRepo
              .create(
                MeaningModel(
                  meaningId = None,
                  wordId = wId,
                  tagId = tId,
                  userId = userId,
                  meaningPostedDate = dateTime,
                  meaningText = entry.text,
                  isJoke = entry.isJoke,
                  meaningGoodCount = 0,
                  meaningHelpfulCount = 0,
                  meaningFunnyCount = 0,
                  isDeleted = false,
                  lastModified = dateTime
                )
              )
              .map(_ => ())
          case None => Future.successful(())
        }

        // 用例（Example）の保存
        _ <- req.example
          .map(entry => entry.copy(text = utils.Validation.trimAll(entry.text)))
          .filter(_.text.nonEmpty) match {
          case Some(entry) =>
            exampleRepo
              .create(
                ExampleModel(
                  exampleId = None,
                  wordId = wId,
                  tagId = tId,
                  userId = userId,
                  examplePostedDate = dateTime,
                  exampleText = entry.text,
                  isJoke = entry.isJoke,
                  exampleGoodCount = 0,
                  exampleHelpfulCount = 0,
                  exampleFunnyCount = 0,
                  isDeleted = false,
                  lastModified = dateTime
                )
              )
              .map(_ => ())
          case None => Future.successful(())
        }

      } yield Right(
        WordTagPostResponse(
          wordId = wId,
          tagId = tId,
          wordMain = wordObj.wordMain,
          tagName = tagObj.tagName,
          hasMeaning = req.meaning.exists(entry => utils.Validation.trimAll(entry.text).nonEmpty),
          hasExample = req.example.exists(entry => utils.Validation.trimAll(entry.text).nonEmpty)
        )
      )

      finalAction.recover { case ex: Exception =>
        Left(ErrorResponse("500", s"Failed to post word: ${ex.getMessage}"))
      }
    }
  }

  def postMeaning(
      req: MeaningPostRequest,
      userId: Option[String],
      dateTime: Long
  ): Future[Either[ErrorResponse, MeaningViewValue]] = {
    // バリデーションの判定（入力制限）
    val trimmedText = utils.Validation.trimAll(req.meaningText)
    if (!utils.Validation.notBlank(req.meaningText)) {
      Future.successful(Left(ErrorResponse("400", "語釈に空白のみは使用できません")))
    } else if (!Validation.lengthVal(RichTextMaxLength, trimmedText)) {
      Future.successful(Left(ErrorResponse("400", s"語釈の説明は${RichTextMaxLength}文字以内で入力してください")))
    } else {
      // Lexiタグかどうかチェック
      tagRepo.findById(req.tagId).flatMap {
        case Some(tag) if tag.tagName.equalsIgnoreCase("Lexi") =>
          Future.successful(Left(ErrorResponse("403", "「Lexi」タグには投稿できません")))
        case Some(_) | None =>
          val newMeaning = MeaningModel(
            meaningId = None,
            wordId = req.wordId,
            tagId = req.tagId,
            userId = userId,
            meaningPostedDate = dateTime,
            meaningText = trimmedText,
            isJoke = req.isJoke,
            meaningGoodCount = 0,
            meaningHelpfulCount = 0,
            meaningFunnyCount = 0,
            isDeleted = false,
            lastModified = dateTime
          )

          // 1. まず Meaning を保存
          meaningRepo
            .create(newMeaning)
            .flatMap { id =>
              val fUser = userId match {
                case Some(uid) => userRepo.findById(uid)
                case None      => Future.successful(None)
              }

              for {
                _ <- notificationService.createFollowNotification(
                  wordId = req.wordId,
                  tagId = req.tagId,
                  contentTypeId = "1",
                  contentId = id,
                  followType = "TAG",
                  postUserId = userId.getOrElse("")
                )
                _ <- notificationService.createFollowNotification(
                  wordId = req.wordId,
                  tagId = req.tagId,
                  contentTypeId = "1",
                  contentId = id,
                  followType = "WORD",
                  postUserId = userId.getOrElse("")
                )
                maybeUser <- fUser
              } yield {
                val name = maybeUser.map(_.userName).getOrElse("Unknown User")

                Right(
                  MeaningViewValue(
                    meaning = ApiModelConversions.meaning(newMeaning.copy(meaningId = Some(id))),
                    userName = name,
                    displayId = maybeUser.map(_.displayId), // ← 追加
                    isGood = false,
                    isHelpful = false,
                    isFunny = false,
                    isFavorite = false
                  )
                )
              }
            }
            .recover { case ex: Exception =>
              Left(ErrorResponse("500", s"Failed to save meaning: ${ex.getMessage}"))
            }
      }
    }
  }

  def postExample(
      req: ExamplePostRequest,
      userId: Option[String],
      dateTime: Long
  ): Future[Either[ErrorResponse, ExampleViewValue]] = {
    // 1. バリデーション（入力制限）
    val trimmedText = utils.Validation.trimAll(req.exampleText)
    if (!utils.Validation.notBlank(req.exampleText)) {
      Future.successful(Left(ErrorResponse("400", "用例に空白のみは使用できません")))
    } else if (!Validation.lengthVal(RichTextMaxLength, trimmedText)) {
      Future.successful(Left(ErrorResponse("400", s"例文は${RichTextMaxLength}文字以内で入力してください")))
    } else {
      // Lexiタグかどうかチェック
      tagRepo.findById(req.tagId).flatMap {
        case Some(tag) if tag.tagName.equalsIgnoreCase("Lexi") =>
          Future.successful(Left(ErrorResponse("403", "「Lexi」タグには投稿できません")))
        case Some(_) | None =>
          val newExample = ExampleModel(
            exampleId = None,
            wordId = req.wordId,
            tagId = req.tagId,
            userId = userId,
            examplePostedDate = dateTime,
            exampleText = trimmedText,
            isJoke = req.isJoke,
            exampleGoodCount = 0,
            exampleHelpfulCount = 0,
            exampleFunnyCount = 0,
            isDeleted = false,
            lastModified = dateTime
          )

          // 2. 例文を保存
          exampleRepo
            .create(newExample)
            .flatMap { id =>
              val fUser = userId match {
                case Some(uid) => userRepo.findById(uid)
                case None      => Future.successful(None)
              }

              for {
                _ <- notificationService.createFollowNotification(
                  wordId = req.wordId,
                  tagId = req.tagId,
                  contentTypeId = "2",
                  contentId = id,
                  followType = "TAG",
                  postUserId = userId.getOrElse("")
                )
                _ <- notificationService.createFollowNotification(
                  wordId = req.wordId,
                  tagId = req.tagId,
                  contentTypeId = "2",
                  contentId = id,
                  followType = "WORD",
                  postUserId = userId.getOrElse("")
                )
                maybeUser <- fUser
              } yield {
                val name = maybeUser.map(_.userName).getOrElse("Unknown User")

                Right(
                  ExampleViewValue(
                    example = ApiModelConversions.example(newExample.copy(exampleId = Some(id))),
                    userName = name,
                    displayId = maybeUser.map(_.displayId), // ← 追加
                    isGood = false,
                    isHelpful = false,
                    isFunny = false,
                    isFavorite = false
                  )
                )
              }
            }
            .recover { case ex: Exception =>
              Left(ErrorResponse("500", s"Failed to save example: ${ex.getMessage}"))
            }
      }
    }
  }

  // 引数に target ("word" か "tag") を追加
  def getSuggestions(request: SuggestionRequest, target: String): Future[SuggestionResponse] = {
    val q = request.query.trim

    val itemsFuture: Future[Seq[SuggestionItem]] = target match {
      // --- 言葉をサジェストする場合 ---
      case "word" =>
        // 1. 選択中のタグに紐づく言葉（related）を取得
        val relatedFuture = request.relatedTagId match {
          case Some(tId) =>
            wordTagRepo
              .findByTagId(tId)
              .map(_.map { case (wt, word) =>
                SuggestionItem(word.wordId.get, word.wordMain, Some(wt.wordReading), "related")
              })
          case _ => Future.successful(Nil)
        }

        // 2. 全言葉の中から検索（search）を取得
        val searchFuture = if (q.nonEmpty) {
          wordRepo.searchByQuery(q).map(_.map(w => SuggestionItem(w.wordId.get, w.wordMain, None, "search")))
        } else Future.successful(Nil)

        // 3. 両方を合体させて整理
        for {
          related <- relatedFuture
          search <- searchFuture
        } yield {
          val filteredRelated = if (q.nonEmpty) related.filter(_.name.contains(q)) else related
          // 関連を上に、検索結果を下に結合。IDの重複は related を優先して除去。
          (filteredRelated ++ search).distinctBy(_.id)
        }

      // --- タグをサジェストする場合 ---
      case "tag" =>
        val relatedFuture = request.relatedWordId match {
          case Some(wId) =>
            wordTagRepo
              .findByWordId(wId)
              .map(_.map { case (wt, tag) =>
                SuggestionItem(tag.tagId.get, tag.tagName, Some(wt.wordReading), "related")
              })
          case _ => Future.successful(Nil)
        }

        val searchFuture = if (q.nonEmpty) {
          tagRepo.findByKeyword(q).map(_.map(t => SuggestionItem(t.tagId.get, t.tagName, None, "search")))
        } else Future.successful(Nil)

        for {
          related <- relatedFuture
          search <- searchFuture
        } yield {
          val filteredRelated = if (q.nonEmpty) related.filter(_.name.contains(q)) else related
          (filteredRelated ++ search).distinctBy(_.id)
        }
    }

    itemsFuture.map(items => SuggestionResponse(items, request.query))
  }

  def getSearchSuggestions(req: SearchSuggestRequest): Future[SearchSuggestionResponse] = {
    val cleanQuery = req.query.replaceAll("^[\\s\\u3000]+|[\\s\\u3000]+$", "")

    // --- 1. IDの補完（逆引き）フェーズ ---
    // 手入力の場合、名前からIDを特定する
    val fResolvedWordId = if (req.baseWordId.isEmpty && req.baseWordName.isDefined) {
      wordRepo.findByMain(req.baseWordName.get).map(_.headOption.flatMap(_.wordId))
    } else Future.successful(req.baseWordId)

    val fResolvedTagId = if (req.baseTagId.isEmpty && req.baseTagName.isDefined) {
      // --- 修正：全角・半角どちらの「＃」も確実に除去して検索する ---
      val nameToSearch = req.baseTagName.get
        .stripPrefix("#")
        .stripPrefix("＃")
        .trim

      tagRepo.findByName(nameToSearch).map(_.headOption.flatMap(_.tagId))
    } else Future.successful(req.baseTagId)

    val resultFuture = for {
      resolvedWordId <- fResolvedWordId
      resolvedTagId <- fResolvedTagId

      // --- 2. 確定したID（または補完したID）を元に関連データを取得 ---
      relatedRows <- (resolvedWordId, resolvedTagId) match {
        case (Some(wId), _) => wordTagRepo.findByWordId(wId)
        case (_, Some(tId)) => wordTagRepo.findByTagId(tId)
        case _              => Future.successful(Nil)
      }

      // 検索窓の文字パース
      (isTagMode, actualQuery) = cleanQuery match {
        case q if q.startsWith("#") || q.startsWith("＃") => (true, q.stripPrefix("#").stripPrefix("＃"))
        case q                                           => (false, q)
      }

      // 入力中の文字に対するサジェスト（部分一致）
      searchRows <-
        if (actualQuery.nonEmpty) {
          if (isTagMode) wordTagRepo.findTagsByPartialName(actualQuery)
          else wordTagRepo.findWordsByPartialName(actualQuery)
        } else Future.successful(Nil)

    } yield {
      // アイテム生成に使用するリクエスト情報を補完後のIDで更新
      val finalReq = req.copy(baseWordId = resolvedWordId, baseTagId = resolvedTagId)

      // --- 3. 関連アイテムの生成 ---
      val related = relatedRows.collect {
        case (wt, t: TagModel) if resolvedWordId.isDefined =>
          val cleanTagName = t.tagName.stripPrefix("#").stripPrefix("＃")
          createItem(t.tagId.getOrElse(0L), s"#${cleanTagName}", "tag", finalReq, None)
        case (wt, w: Word) if resolvedTagId.isDefined =>
          createItem(w.wordId.getOrElse(0L), w.wordMain, "word", finalReq, Some(wt.wordReading))
      }

      // --- 4. 検索アイテムの生成 ---
      val search = searchRows.flatMap {
        case (wt, t: TagModel) if isTagMode =>
          val cleanTagName = t.tagName.stripPrefix("#").stripPrefix("＃")
          Some(createItem(t.tagId.getOrElse(0L), s"#${cleanTagName}", "tag", finalReq, None))
        case (wt, w: Word) if !isTagMode =>
          Some(createItem(w.wordId.getOrElse(0L), w.wordMain, "word", finalReq, None))
        case _ => None
      }

      // フィルタリングと合体
      val filteredRelated = if (actualQuery.isEmpty) related else related.filter(_.name.contains(actualQuery))
      val relatedIds = filteredRelated.map(_.id).toSet
      val uniqueSearch = search.filterNot(item => relatedIds.contains(item.id))
      // 名前でも重複除去（同じ名前の異なるIDを除外）
      (filteredRelated ++ uniqueSearch).distinctBy(item => (item.name, item.sourceType))
    }

    resultFuture.map(SearchSuggestionResponse(_, req.query))
  }

  private def createItem(
      id: Long,
      name: String,
      sType: String,
      req: SearchSuggestRequest,
      reading: Option[String] = None
  ): SearchSuggestionItem = {
    SearchSuggestionItem(
      id = id,
      name = name,
      reading = reading,
      sourceType = sType,
      baseId = req.baseWordId.orElse(req.baseTagId),
      baseType =
        if (req.baseWordId.isDefined) Some("word")
        else if (req.baseTagId.isDefined) Some("tag")
        else None
    )
  }

  // 語釈を削除する（論理削除）
  def deleteMeaning(meaningId: Long, userId: String): Future[Either[ErrorResponse, Unit]] = {
    meaningRepo.findById(meaningId).flatMap {
      case Some(meaning) if meaning.userId.contains(userId) =>
        meaningRepo
          .logicalDelete(meaningId)
          .map { _ =>
            Right(())
          }
          .recover { case ex: Exception =>
            Left(ErrorResponse("500", s"語釈の削除に失敗しました: ${ex.getMessage}"))
          }
      case Some(_) =>
        Future.successful(Left(ErrorResponse("403", "自分の投稿以外は削除できません")))
      case None =>
        Future.successful(Left(ErrorResponse("404", "語釈が見つかりません")))
    }
  }

  // 用例を削除する（論理削除）
  def deleteExample(exampleId: Long, userId: String): Future[Either[ErrorResponse, Unit]] = {
    exampleRepo.findById(exampleId).flatMap {
      case Some(example) =>
        if (example.userId.contains(userId)) {
          exampleRepo.logicalDelete(exampleId).map(_ => Right(())).recover { case ex: Exception =>
            Left(ErrorResponse("500", s"削除失敗: ${ex.getMessage}"))
          }
        } else {
          Future.successful(Left(ErrorResponse("403", s"自分の投稿以外は削除できません")))
        }

      case None =>
        Future.successful(Left(ErrorResponse("404", "用例が見つかりません")))
    }
  }

  // 語釈を編集する
  def updateMeaning(
      meaningId: Long,
      newText: String,
      userId: String
  ): Future[Either[ErrorResponse, MeaningViewValue]] = {
    val trimmedText = utils.Validation.trimAll(newText)
    if (!utils.Validation.notBlank(newText)) {
      Future.successful(Left(ErrorResponse("400", "語釈に空白のみは使用できません")))
    } else if (!Validation.lengthVal(RichTextMaxLength, trimmedText)) {
      Future.successful(Left(ErrorResponse("400", s"語釈の説明は${RichTextMaxLength}文字以内で入力してください")))
    } else {
      meaningRepo.findById(meaningId).flatMap {
        case Some(meaning) if meaning.userId.contains(userId) =>
          val now = System.currentTimeMillis()
          val updated = meaning.copy(meaningText = trimmedText, lastModified = now)
          meaningRepo
            .update(updated)
            .flatMap { _ =>
              userRepo.findById(userId).map { maybeUser =>
                Right(
                  MeaningViewValue(
                    meaning = ApiModelConversions.meaning(updated),
                    userName = maybeUser.map(_.userName).getOrElse("Unknown User"),
                    displayId = maybeUser.map(_.displayId), // ← 追加
                    isGood = false,
                    isHelpful = false,
                    isFunny = false,
                    isFavorite = false
                  )
                )
              }
            }
            .recover { case ex: Exception =>
              Left(ErrorResponse("500", s"語釈の更新に失敗しました: ${ex.getMessage}"))
            }
        case Some(_) =>
          Future.successful(Left(ErrorResponse("403", "自分の投稿以外は編集できません")))
        case None =>
          Future.successful(Left(ErrorResponse("404", "語釈が見つかりません")))
      }
    }
  }

  // 用例を編集する
  def updateExample(
      exampleId: Long,
      newText: String,
      userId: String
  ): Future[Either[ErrorResponse, ExampleViewValue]] = {
    val trimmedText = utils.Validation.trimAll(newText)
    if (!utils.Validation.notBlank(newText)) {
      Future.successful(Left(ErrorResponse("400", "用例に空白のみは使用できません")))
    } else if (!Validation.lengthVal(RichTextMaxLength, trimmedText)) {
      Future.successful(Left(ErrorResponse("400", s"例文は${RichTextMaxLength}文字以内で入力してください")))
    } else {
      exampleRepo.findById(exampleId).flatMap {
        case Some(example) if example.userId.contains(userId) =>
          val now = System.currentTimeMillis()
          val updated = example.copy(exampleText = trimmedText, lastModified = now)
          exampleRepo
            .update(updated)
            .flatMap { _ =>
              userRepo.findById(userId).map { maybeUser =>
                Right(
                  ExampleViewValue(
                    example = ApiModelConversions.example(updated),
                    userName = maybeUser.map(_.userName).getOrElse("Unknown User"),
                    displayId = maybeUser.map(_.displayId), // ← 追加
                    isGood = false,
                    isHelpful = false,
                    isFunny = false,
                    isFavorite = false
                  )
                )
              }
            }
            .recover { case ex: Exception =>
              Left(ErrorResponse("500", s"用例の更新に失敗しました: ${ex.getMessage}"))
            }
        case Some(_) =>
          Future.successful(Left(ErrorResponse("403", "自分の投稿以外は編集できません")))
        case None =>
          Future.successful(Left(ErrorResponse("404", "用例が見つかりません")))
      }
    }
  }

}
