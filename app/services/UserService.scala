package services
import models.db.{FavoriteModel, FollowModel}

import javax.inject._
import scala.concurrent.{ExecutionContext, Future}
import repositories._
import models._
import utils.Format
import org.mindrot.jbcrypt.BCrypt
import play.api.Logger
import shared.api.ErrorResponse
import models.db.DeletedUserModel
import models.ApiModelConversions
import shared.api.{ExampleViewValue, MeaningViewValue}

@Singleton
class UserService @Inject() (
    wordRepo: WordRepository,
    tagRepo: TagModelRepository,
    wordTagRepo: WordTagModelRepository,
    meaningRepo: MeaningModelRepository,
    exampleRepo: ExampleModelRepository,
    deletedUserRepo: DeletedUserModelRepository,
    userRepo: UserRepository,
    favoriteRepo: FavoriteModelRepository,
    threadRepo: ThreadModelRepository,
    commentRepo: CommentModelRepository,
    commentReactionRepo: CommentReactionModelRepository,
    reactionRepo: ReactionModelRepository,
    followRepo: FollowModelRepository,
    viewHistoryRepo: ViewHistoryModelRepository,
    contentQuoteRepo: ContentQuoteRepository
)(implicit ec: ExecutionContext) {

  private val logger = Logger(this.getClass)

  def getMyProfile(userId: String): Future[MyProfileResponse] = {
    for {
      userOpt <- userRepo.findById(userId)
      meaningCount <- meaningRepo.countByUserId(userId)
      exampleCount <- exampleRepo.countByUserId(userId)
      threadCount <- threadRepo.countByUserId(userId)
      commentCount <- commentRepo.countByUserId(userId)
      favoriteCount <- favoriteRepo.countByUserId(userId)
    } yield {
      val user = userOpt.getOrElse(throw new Exception("User not found"))
      MyProfileResponse(
        userId = user.userId,
        displayId = user.displayId, // ← 追加
        userName = user.userName,
        totalPosts = meaningCount + exampleCount, // 意味と用例を合算
        totalThreads = threadCount,
        totalComments = commentCount,
        totalFavorites = favoriteCount
      )
    }
  }

  // --- 自分の投稿（用例） ---
  def getMyExamples(currentUserId: String): Future[Seq[MyPageExampleViewValue]] = {
    exampleRepo.findByUserIdWithWordTag(currentUserId).map { rows =>
      rows.map { case (e, uOpt, w, t) =>
        MyPageExampleViewValue(
          exampleView = ExampleViewValue(
            example = ApiModelConversions.example(e),
            userName = uOpt.map(_.userName).getOrElse("unknown"),
            displayId = uOpt.map(_.displayId)
          ),
          wordMain = w.wordMain,
          tagName = t.tagName,
          favoriteId = None
        )
      }
    }
  }

  def getMyMeanings(currentUserId: String): Future[Seq[MyPageMeaningViewValue]] = {
    // リポジトリで Word と Tag も Join して取得するように調整が必要
    meaningRepo.findByUserIdWithWordTag(currentUserId).map { rows =>
      rows.map { case (m, uOpt, w, t) =>
        MyPageMeaningViewValue(
          meaningView = MeaningViewValue(
            meaning = ApiModelConversions.meaning(m),
            userName = uOpt.map(_.userName).getOrElse("unknown"),
            displayId = uOpt.map(_.displayId)
          ),
          wordMain = w.wordMain,
          tagName = t.tagName,
          favoriteId = None
        )
      }
    }
  }

  def getMyWords(currentUserId: String): Future[Seq[MyPageWordTagViewValue]] = {
    wordTagRepo.findByUserIdWithWordTag(currentUserId).map { rows =>
      rows.map { case (wt, w, t) =>
        MyPageWordTagViewValue(
          wordId = wt.wordId,
          tagId = wt.tagId,
          wordMain = w.wordMain,
          tagName = t.tagName,
          wordReading = wt.wordReading,
          wordCreateDate = wt.wordCreateDate
        )
      }
    }
  }

  def getMyComments(currentUserId: String): Future[Seq[CommentViewValue]] = {
    for {
      // 1. 自分のコメントとユーザー情報を取得
      rows <- commentRepo.findByUserId(currentUserId)
      commentIds = rows.map(_._1.commentId.getOrElse(0L))

      // 2. 自分のコメントに対するリアクション（Good）を取得
      // ※引数の型を currentUserId (String) に合わせてリポジトリも調整済みと想定
      userReactions <- commentReactionRepo.findByCommentIds(currentUserId, commentIds)
      quotes <- contentQuoteRepo.findForCommentIds(commentIds)
    } yield {
      rows.map { case (c, u) =>
        val cId = c.commentId.getOrElse(0L)

        // このコメントに対する自分のリアクションがあるか確認
        val myReactionOpt = userReactions.find(_.commentId == cId)

        CommentViewValue(
          comment = c,
          userName = u.userName,
          displayId = Some(u.displayId),
          isBestAnswer = false, // 必要に応じて判定
          isGood = myReactionOpt.exists(_.isGood),
          quote = quotes.get(cId)
        )
      }
    }
  }

  def getMyThreads(currentUserId: String): Future[Seq[ThreadViewValue]] = {
    threadRepo.findByUserId(currentUserId).flatMap { rows =>
      val threadIds = rows.flatMap(_._1.threadId)
      contentQuoteRepo.findForThreadIds(threadIds).map { quotes =>
        rows.map { case (t, optQuestion, optUser, tt) =>
          val tId = t.threadId.getOrElse(0L)

          // 質問ステータスの組み立て
          val questionStatus = if (t.threadTypeId == "1" || t.threadTypeId == "2") {
            optQuestion.map { q =>
              QuestionStatusViewValue(
                isResolved = q.resolvedDate.isDefined || q.bestCommentId.isDefined,
                resolvedDate = q.resolvedDate.map(ts => Format.formatDate(ts)),
                bestCommentId = q.bestCommentId
              )
            }
          } else None

          ThreadViewValue(
            threadId = tId,
            userId = t.userId, // ← 内部用：オーナーシップ確認
            displayId = optUser.map(_.displayId), // ← 公開用：画面表示
            // DBから取得したユーザー名、なければ "Unknown"
            userName = optUser.map(_.userName).getOrElse("Unknown"),
            threadTitle = t.threadTitle,
            threadCreateDate = t.threadCreateDate,
            threadType = tt,
            wordId = t.wordId,
            tagId = t.tagId,
            questionStatus = questionStatus,
            commentCount = t.threadCommentCount,
            quote = quotes.get(tId)
          )
        }
      }
    }
  }
  // UserService.scala

  def getMyFavoriteMeanings(currentUserId: String): Future[Seq[MyPageMeaningViewValue]] = {
    favoriteRepo.findFavoriteMeaningsByUserId(currentUserId).map { rows =>
      rows.map { case (f, m, uOpt, w, t) =>
        MyPageMeaningViewValue(
          meaningView = MeaningViewValue(
            meaning = ApiModelConversions.meaning(m),
            userName = uOpt.map(_.userName).getOrElse("unknown"),
            displayId = uOpt.map(_.displayId),
            isFavorite = true
          ),
          wordMain = w.wordMain,
          tagName = t.tagName,
          favoriteId = f.favoriteId
        )
      }
    }
  }

  def getMyFavoriteExamples(currentUserId: String): Future[Seq[MyPageExampleViewValue]] = {
    favoriteRepo.findFavoriteExamplesByUserId(currentUserId).map { rows =>
      rows.map { case (f, e, uOpt, w, t) =>
        MyPageExampleViewValue(
          exampleView = ExampleViewValue(
            example = ApiModelConversions.example(e),
            userName = uOpt.map(_.userName).getOrElse("unknown"),
            displayId = uOpt.map(_.displayId),
            isFavorite = true
          ),
          wordMain = w.wordMain,
          tagName = t.tagName,
          favoriteId = f.favoriteId
        )
      }
    }
  }

  def getMyReactedMeanings(currentUserId: String): Future[Seq[MyPageMeaningViewValue]] = {
    reactionRepo.findMeaningReactionsByUserId(currentUserId).map { rows =>
      rows
        .groupBy { case (_, _, m, _, _, _) => m.meaningId.getOrElse(0L) }
        .values
        .toSeq
        .sortBy(group => -group.map(_._1.reactionDate).max)
        .map { group =>
          val (_, _, m, uOpt, w, t) = group.head
          val types = group.map(_._2).toSet
          MyPageMeaningViewValue(
            meaningView = MeaningViewValue(
              meaning = ApiModelConversions.meaning(m),
              userName = uOpt.map(_.userName).getOrElse("unknown"),
              displayId = uOpt.map(_.displayId),
              isGood = types.contains("good"),
              isHelpful = types.contains("helpful"),
              isFunny = types.contains("funny")
            ),
            wordMain = w.wordMain,
            tagName = t.tagName,
            favoriteId = None
          )
        }
    }
  }

  def getMyReactedExamples(currentUserId: String): Future[Seq[MyPageExampleViewValue]] = {
    reactionRepo.findExampleReactionsByUserId(currentUserId).map { rows =>
      rows
        .groupBy { case (_, _, e, _, _, _) => e.exampleId.getOrElse(0L) }
        .values
        .toSeq
        .sortBy(group => -group.map(_._1.reactionDate).max)
        .map { group =>
          val (_, _, e, uOpt, w, t) = group.head
          val types = group.map(_._2).toSet
          MyPageExampleViewValue(
            exampleView = ExampleViewValue(
              example = ApiModelConversions.example(e),
              userName = uOpt.map(_.userName).getOrElse("unknown"),
              displayId = uOpt.map(_.displayId),
              isGood = types.contains("good"),
              isHelpful = types.contains("helpful"),
              isFunny = types.contains("funny")
            ),
            wordMain = w.wordMain,
            tagName = t.tagName,
            favoriteId = None
          )
        }
    }
  }

  // --- タグのフォロー ---
  def followTag(userId: String, tagId: Long): Future[FollowModel] = {
    followRepo.findExisting(userId, tagId, None).flatMap {
      case Some(existing) => Future.successful(existing)
      case None           =>
        val newFollow = FollowModel(
          followId = None,
          userId = userId,
          tagId = tagId,
          wordId = None,
          isWordContained = false,
          followDate = System.currentTimeMillis()
        )
        followRepo.create(newFollow)
    }
  }

  // タグフォロー一覧
  // タグフォロー一覧
  def getFollowedTags(userId: String): Future[Either[ErrorResponse, TagFollowsResponse]] = {
    followRepo
      .findFollowedTags(userId)
      .map { rows =>
        val items = rows.map { case (f, t) =>
          FollowItemViewValue(
            followId = f.followId.getOrElse(0L),
            tagId = t.tagId.getOrElse(0L),
            tagName = t.tagName,
            wordId = None, // タグフォローなので None
            wordMain = None, // タグフォローなので None
            followDate = f.followDate, // Long型をそのまま渡す
            isWordContained = false // タグフォローなので false
          )
        }
        Right(TagFollowsResponse(items))
      }
      .recover { case e => Left(ErrorResponse("500", e.getMessage)) }
  }

  // ワードフォロー一覧
  def getFollowedWords(userId: String): Future[Either[ErrorResponse, WordFollowsResponse]] = {
    followRepo
      .findFollowedWords(userId)
      .map { rows =>
        val items = rows.map { case (f, w, t) =>
          FollowItemViewValue(
            followId = f.followId.getOrElse(0L),
            tagId = t.tagId.getOrElse(0L),
            tagName = t.tagName,
            wordId = w.wordId, // Option[Long]
            wordMain = Some(w.wordMain), // Option[String]
            followDate = f.followDate,
            isWordContained = true // ワードフォローなので true
          )
        }
        Right(WordFollowsResponse(items))
      }
      .recover { case e => Left(ErrorResponse("500", e.getMessage)) }
  }

  // --- 単語のフォローをトグル ---
  def unfollow(followId: Long, userId: String): Future[Boolean] = {
    followRepo.deleteByIdAndUser(followId, userId).map(_ > 0)
  }

  /** 単語のフォロー (トグル操作) 「タグ＋言葉」の組み合わせが存在すれば削除、なければ追加します。
    */
  def toggleWordFollow(userId: String, tagId: Long, wordId: Long): Future[Boolean] = {
    followRepo.findExisting(userId, tagId, Some(wordId)).flatMap {
      case Some(existing) =>
        followRepo.deleteById(existing.followId.get).map(_ => false) // 削除したので false
      case None =>
        val newFollow = FollowModel(
          followId = None,
          userId = userId,
          tagId = tagId,
          wordId = Some(wordId),
          isWordContained = true,
          followDate = System.currentTimeMillis()
        )
        followRepo.create(newFollow).map(_ => true) // 追加したので true
    }
  }

  def getUserHistory(userId: String): Future[ViewHistoryResponse] = {
    viewHistoryRepo.findByUserId(userId).map { histories =>
      ViewHistoryResponse(viewHistories = histories)
    }
  }

  def recordView(userId: String, wordId: Long, tagId: Long): Future[Unit] = {
    viewHistoryRepo.upsertViewHistory(userId, wordId, tagId).map(_ => ())
  }

  /** お気に入りの状態を反転させる
    * @return
    *   Future[Boolean] (true: 登録された, false: 削除された)
    */
  def toggleFavorite(userId: String, contentTypeId: String, contentId: Long): Future[Boolean] = {
    favoriteRepo.findExisting(userId, contentTypeId, contentId).flatMap {
      case Some(existing) =>
        // 存在する場合：削除
        favoriteRepo.delete(existing.favoriteId.get).map(_ => false)
      case None =>
        // 存在しない場合：新規登録
        val newFavorite = FavoriteModel(
          favoriteId = None,
          userId = userId,
          contentTypeId = contentTypeId,
          contentId = contentId,
          favoriteDate = System.currentTimeMillis()
        )
        favoriteRepo.create(newFavorite).map(_ => true)
    }
  }

  // 補助メソッド: Long(ミリ秒)を "yyyy/MM/dd" 形式の文字列に変換

  def updateUserName(userId: String, newUserName: String): Future[Either[ErrorResponse, Unit]] = {
    // バリデーション
    val trimmedName = utils.Validation.trimAll(newUserName)
    if (!utils.Validation.notBlank(newUserName)) {
      Future.successful(Left(ErrorResponse("400", "ユーザー名に空白のみは使用できません")))
    } else if (trimmedName.length > 50) {
      Future.successful(Left(ErrorResponse("400", "ユーザー名は50文字以内で入力してください")))
    } else {
      userRepo.findById(userId).flatMap {
        case Some(user) =>
          val updatedUser = user.copy(userName = trimmedName)
          userRepo.update(updatedUser).map(_ => Right(()))
        case None =>
          Future.successful(Left(ErrorResponse("404", "ユーザーが見つかりません")))
      }
    }
  }

  def updatePassword(
      userId: String,
      currentPassword: String,
      newPassword: String
  ): Future[Either[ErrorResponse, Unit]] = {
    // バリデーション
    if (currentPassword.isEmpty || newPassword.isEmpty) {
      Future.successful(Left(ErrorResponse("400", "パスワードを入力してください")))
    } else if (!utils.Validation.isValidPassword(newPassword)) {
      Future.successful(Left(ErrorResponse("400", "新しいパスワードは8文字以上、UTF-8で72バイト以内にしてください")))
    } else {
      userRepo.findById(userId).flatMap {
        case Some(user) =>
          // 現在のパスワードを確認
          if (BCrypt.checkpw(currentPassword, user.userPassHash)) {
            val hashedNewPassword = BCrypt.hashpw(newPassword, BCrypt.gensalt())
            userRepo.updatePasswordAndIncrementTokenVersion(userId, hashedNewPassword).map(_ => Right(()))
          } else {
            Future.successful(Left(ErrorResponse("401", "現在のパスワードが正しくありません")))
          }
        case None =>
          Future.successful(Left(ErrorResponse("404", "ユーザーが見つかりません")))
      }
    }
  }

  def deleteAccount(userId: String, password: String): Future[Either[ErrorResponse, Unit]] = {
    if (password.isEmpty) {
      Future.successful(Left(ErrorResponse("400", "パスワードを入力してください")))
    } else {
      userRepo.findById(userId).flatMap {
        case Some(user) =>
          // パスワードを確認
          if (BCrypt.checkpw(password, user.userPassHash)) {
            // 1. Deleted_Usersテーブルに削除記録を追加
            val deletedUserRecord = DeletedUserModel(
              deletedId = None,
              userId = userId,
              userName = user.userName,
              deleteDate = System.currentTimeMillis(),
              deleteReason = "ユーザー自身による削除"
            )

            for {
              // 削除記録を追加
              _ <- deletedUserRepo.create(deletedUserRecord)

              // 2. ユーザー情報を論理削除（「削除済みユーザー」に変更）
              randomPassword = java.util.UUID.randomUUID().toString
              updatedUser = user.copy(
                userName = "削除済みユーザー",
                userPassHash = BCrypt.hashpw(randomPassword, BCrypt.gensalt()),
                tokenVersion = user.tokenVersion + 1
              )
              _ <- userRepo.update(updatedUser)
            } yield Right(())
          } else {
            Future.successful(Left(ErrorResponse("401", "パスワードが正しくありません")))
          }
        case None =>
          Future.successful(Left(ErrorResponse("404", "ユーザーが見つかりません")))
      }
    }
  }

}
