package services

import javax.inject._
import scala.concurrent.{ExecutionContext, Future}
import repositories._
import models._
import utils.{Validation, Format}
import play.api.Logger
import shared.api.*
import models.db.{CommentReactionModel, ThreadModel}

@Singleton
class CommunityService @Inject() (
    threadRepo: ThreadModelRepository,
    threadQRepo: ThreadQuestionModelRepository,
    threadTypeRepo: ThreadTypeRepository,
    commentRepo: CommentModelRepository,
    userRepo: UserRepository,
    commentReactionRepo: CommentReactionModelRepository,
    communityPostRepo: CommunityPostRepository,
    contentQuoteRepo: ContentQuoteRepository,
    notificationService: NotificationService
)(implicit ec: ExecutionContext) {

  private val logger = Logger(this.getClass)

  // ThreadService.scala
  def findThreads(wordId: Long, tagId: Long): Future[Seq[ThreadViewValue]] = {
    // リポジトリが (ThreadModel, Option[TQ], ThreadType, Option[User]) を返す前提
    threadRepo.findByWordAndTag(wordId, tagId).flatMap { rows =>
      val threadIds = rows.flatMap(_._1.threadId)
      contentQuoteRepo.findForThreadIds(threadIds).map { quotes =>
        rows.map {
          case (thread, optQuestion, threadType, optUser) => // optUser を追加

            val questionStatus = if (thread.threadTypeId == "2") {
              optQuestion.flatMap { q =>
                Some(
                  QuestionStatusViewValue(
                    isResolved = q.resolvedDate.isDefined,
                    resolvedDate = q.resolvedDate.map(ts => Format.formatDate(ts)),
                    bestCommentId = q.bestCommentId
                  )
                )
              }
            } else None

            val threadId = thread.threadId.getOrElse(0L)
            ThreadViewValue(
              threadId = threadId,
              userId = thread.userId, // ← 内部用：オーナーシップ確認
              displayId = optUser.map(_.displayId), // ← 公開用：画面表示
              // ★ thread.userName ではなく、結合した optUser から取得する
              userName = optUser.map(_.userName).getOrElse("削除済みユーザー"),
              threadTitle = thread.threadTitle,
              threadCreateDate = thread.threadCreateDate,
              threadType = threadType,
              wordId = thread.wordId,
              tagId = thread.tagId,
              questionStatus = questionStatus,
              commentCount = thread.threadCommentCount,
              quote = quotes.get(threadId)
            )
        }
      }
    }
  }

  /** ページネーション対応：スレッドを取得 */
  def findThreadsPaginated(
      wordId: Long,
      tagId: Long,
      lastId: Option[Long],
      limit: Int = 10
  ): Future[Seq[ThreadViewValue]] = {
    threadRepo.findByWordAndTagPaginated(wordId, tagId, lastId, limit).flatMap { rows =>
      val threadIds = rows.flatMap(_._1.threadId)
      contentQuoteRepo.findForThreadIds(threadIds).map { quotes =>
        rows.map { case (thread, optQuestion, threadType, optUser) =>
          val questionStatus = if (thread.threadTypeId == "2") {
            optQuestion.flatMap { q =>
              Some(
                QuestionStatusViewValue(
                  isResolved = q.resolvedDate.isDefined,
                  resolvedDate = q.resolvedDate.map(ts => Format.formatDate(ts)),
                  bestCommentId = q.bestCommentId
                )
              )
            }
          } else None

          val threadId = thread.threadId.getOrElse(0L)
          ThreadViewValue(
            threadId = threadId,
            userId = thread.userId, // ← 内部用：オーナーシップ確認
            displayId = optUser.map(_.displayId), // ← 公開用：画面表示
            userName = optUser.map(_.userName).getOrElse("削除済みユーザー"),
            threadTitle = thread.threadTitle,
            threadCreateDate = thread.threadCreateDate,
            threadType = threadType,
            wordId = thread.wordId,
            tagId = thread.tagId,
            questionStatus = questionStatus,
            commentCount = thread.threadCommentCount,
            quote = quotes.get(threadId)
          )
        }
      }
    }
  }

  def getThreadListView: Future[Seq[ThreadViewValue]] = {
    for {
      // 1. スレッド一覧をユーザー・タイプと共に取得（N+1解消）
      threadsWithDetails <- threadRepo.listAllWithDetails()

      // 2. 取得したスレッドの全IDを抽出
      threadIds = threadsWithDetails.map(_._1.threadId.get)

      // 3. コメント数を一括取得
      commentCountMap <- commentRepo.countGroupedByThreadIds(threadIds)
      quotes <- contentQuoteRepo.findForThreadIds(threadIds)

      // 4. ThreadViewValue にマッピング（Future.sequenceは不要）
      viewValues = threadsWithDetails.map { case (thread, threadType, userOpt) =>
        ThreadViewValue(
          threadId = thread.threadId.get,
          userId = thread.userId, // ← 内部用：オーナーシップ確認
          displayId = userOpt.map(_.displayId), // ← 公開用：画面表示
          userName = userOpt.map(_.userName).getOrElse("Unknown"),
          threadTitle = thread.threadTitle,
          threadCreateDate = thread.threadCreateDate,
          threadType = threadType,
          wordId = thread.wordId,
          tagId = thread.tagId,
          commentCount = commentCountMap.getOrElse(thread.threadId.get, 0),
          quote = quotes.get(thread.threadId.get)
        )
      }
    } yield viewValues
  }

  def createComment(comment: CommentModel): Future[Long] = {
    for {
      newCommentId <- commentRepo.create(comment)
      _ <- threadRepo.updateCommentCount(comment.threadId, 1)
    } yield newCommentId
  }

  // CommunityService.scala

  // app/services/CommunityService.scala

  def findThreadDetail(
      threadId: Long,
      currentUserIdOpt: Option[String]
  ): Future[Either[ErrorResponse, ThreadDetailResponse]] = {
    val fThreadModel = threadRepo.findActiveById(threadId)
    val fComments = findComments(threadId, currentUserIdOpt)
    val fQuestion = threadQRepo.findByThreadId(threadId)
    val fQuote = contentQuoteRepo.findForTarget(ContentQuoteTypes.Thread, threadId)

    for {
      maybeThread <- fThreadModel
      comments <- fComments
      maybeQuestion <- fQuestion
      quote <- fQuote

      // ★ 修正ポイント1: スレッド作成者のユーザー情報を取得する
      // maybeThread が Some の場合のみ、その userId を使ってユーザーを検索
      maybeUser <- maybeThread match {
        case Some(t) => userRepo.findById(t.userId.getOrElse(""))
        case None    => Future.successful(None)
      }

      maybeType <- maybeThread match {
        case Some(t) => threadTypeRepo.findById(t.threadTypeId)
        case None    => Future.successful(None)
      }
    } yield {
      maybeThread match {
        case Some(t) =>
          val threadType = maybeType.getOrElse(ThreadType(t.threadTypeId, "Unknown"))

          val questionStatus = maybeQuestion.map { q =>
            QuestionStatusViewValue(
              isResolved = q.bestCommentId.isDefined,
              resolvedDate = None,
              bestCommentId = q.bestCommentId
            )
          }

          // ★ 修正ポイント2: ThreadViewValue の組み立て
          val threadView = ThreadViewValue(
            threadId = t.threadId.getOrElse(threadId),
            userId = t.userId, // ← 内部用：オーナーシップ確認
            displayId = maybeUser.map(_.displayId), // ← 公開用：画面表示
            // IDではなく、取得したユーザー名を入れる。存在しなければ「削除済みユーザー」
            userName = maybeUser.map(_.userName).getOrElse("削除済みユーザー"),
            threadTitle = t.threadTitle,
            threadCreateDate = t.threadCreateDate,
            threadType = threadType,
            wordId = t.wordId,
            tagId = t.tagId,
            questionStatus = questionStatus,
            commentCount = comments.length,
            quote = quote
          )

          Right(ThreadDetailResponse(thread = threadView, comments = comments))

        case None =>
          Left(ErrorResponse(s"Thread $threadId not found", "NOT_FOUND"))
      }
    }
  }

  /** ページネーション対応：コメントを取得 */
  def findCommentsPaginated(
      threadId: Long,
      currentUserIdOpt: Option[String],
      lastId: Option[Long],
      limit: Int = 10
  ): Future[Seq[CommentViewValue]] = {
    for {
      maybeQuestion <- threadQRepo.findByThreadId(threadId)
      commentsWithUser <- commentRepo.findByThreadIdWithUserPaginated(threadId, lastId, limit)
      commentIds = commentsWithUser.map(_._1.commentId.getOrElse(0L))
      quotes <- contentQuoteRepo.findForCommentIds(commentIds)

      userGoods <- currentUserIdOpt match {
        case Some(uid) => commentReactionRepo.findByCommentIds(uid, commentIds)
        case None      => Future.successful(Nil)
      }
    } yield {
      val bestCommentId = maybeQuestion.flatMap(_.bestCommentId)
      commentsWithUser.map { case (comment, userName) =>
        val cId = comment.commentId.getOrElse(0L)
        val reaction = userGoods.find(_.commentId == cId)
        CommentViewValue(
          comment = comment,
          userName = userName,
          displayId = None, // ← 追加（後でユーザー情報から取得するよう修正が必要）
          isGood = reaction.exists(_.isGood),
          isBad = reaction.exists(!_.isGood),
          isBestAnswer = bestCommentId.contains(cId),
          quote = quotes.get(cId)
        )
      }
    }
  }

  private def findComments(threadId: Long, currentUserIdOpt: Option[String]): Future[Seq[CommentViewValue]] = {
    for {
      maybeQuestion <- threadQRepo.findByThreadId(threadId)
      commentsWithUser <- commentRepo.findByThreadIdWithUser(threadId)
      commentIds = commentsWithUser.map(_._1.commentId.getOrElse(0L))
      quotes <- contentQuoteRepo.findForCommentIds(commentIds)

      // ログイン中なら、このスレッド内の各コメントへの自分のリアクションをDBから一括取得
      userReactions <- currentUserIdOpt match {
        case Some(uid) => commentReactionRepo.findByCommentIds(uid, commentIds)
        case None      => Future.successful(Seq.empty[CommentReactionModel])
      }
    } yield {
      val bestCommentId = maybeQuestion.flatMap(_.bestCommentId)
      // 判定用に Map(commentId -> isGood) を作成
      val reactionMap = userReactions.map(r => r.commentId -> r.isGood).toMap

      commentsWithUser.map { case (comment, userName) =>
        val cId = comment.commentId.getOrElse(0L)
        val myReaction = reactionMap.get(cId) // Option[Boolean]

        CommentViewValue(
          comment = comment,
          userName = userName,
          displayId = None, // ← 追加（後でユーザー情報から取得するよう修正が必要）
          isBestAnswer = bestCommentId.contains(cId),
          // is_good が true なら Good, false なら Bad
          isGood = myReaction.contains(true),
          isBad = myReaction.contains(false),
          quote = quotes.get(cId)
        )
      }
    }
  }

  def postComment(req: CommentPostRequest, userId: String): Future[Either[ErrorResponse, CommentViewValue]] = {
    if (!Validation.lengthVal(400, req.commentText)) {
      Future.successful(Left(ErrorResponse("400", "コメントは400文字以内で入力してください")))
    } else {
      val now = System.currentTimeMillis()
      val newComment = CommentModel(
        commentId = None,
        threadId = req.threadId,
        userId = Some(userId),
        parentCommentId = req.parentCommentId,
        commentDate = now,
        commentText = req.commentText,
        commentGoodCount = 0,
        commentBadCount = 0,
        isDeleted = false,
        lastModified = now
      )

      val action = for {
        savedComment <- communityPostRepo.createComment(newComment, req.quote, userId)
        maybeQuestion <- threadQRepo.findByThreadId(req.threadId)
        maybeUser <- userRepo.findById(userId)
        _ <- notificationService.createCommentNotification(
          threadId = req.threadId,
          commentId = savedComment.commentId.getOrElse(0L),
          parentCommentId = req.parentCommentId,
          commentUserId = userId
        )
        quote <- contentQuoteRepo.findForTarget(ContentQuoteTypes.Comment, savedComment.commentId.getOrElse(0L))
      } yield {
        val isBest = maybeQuestion.flatMap(_.bestCommentId).contains(savedComment.commentId.getOrElse(-1L))

        Right(
          CommentViewValue(
            comment = savedComment,
            userName = maybeUser.map(_.userName).getOrElse("Unknown User"),
            displayId = maybeUser.map(_.displayId),
            isBestAnswer = isBest,
            quote = quote
          )
        )
      }

      action.recover {
        case e: InvalidQuoteException         => Left(ErrorResponse("400", e.reason))
        case e: InvalidCommentTargetException => Left(ErrorResponse("404", e.reason))
        case e: Exception                     => Left(ErrorResponse("500", s"Database error: ${e.getMessage}"))
      }
    }
  }

  def postThread(req: ThreadPostRequest, userId: String): Future[Either[ErrorResponse, ThreadViewValue]] = {
    // 1. バリデーション
    val trimmedTitle = utils.Validation.trimAll(req.threadTitle)
    if (!utils.Validation.notBlank(req.threadTitle)) {
      Future.successful(Left(ErrorResponse("400", "タイトルに空白のみは使用できません")))
    } else if (!Validation.lengthVal(100, trimmedTitle)) {
      Future.successful(Left(ErrorResponse("400", "タイトルは100文字以内で入力してください")))
    } else {
      val now = System.currentTimeMillis()
      val newThread = ThreadModel(
        threadId = None,
        wordId = req.wordId,
        tagId = req.tagId,
        userId = Some(userId),
        threadTypeId = req.threadTypeId,
        threadCreateDate = now,
        threadTitle = trimmedTitle,
        threadCommentCount = 0,
        isDeleted = false,
        lastModified = now
      )

      communityPostRepo
        .createThread(newThread, req.quote, userId)
        .flatMap { created =>
          val savedThread = created.thread
          val threadId = savedThread.threadId.getOrElse(0L)

          for {
            maybeUser <- userRepo.findById(userId)
            _ <- notificationService.createFollowNotification(
              wordId = req.wordId,
              tagId = req.tagId,
              contentTypeId = "3", // Thread
              contentId = threadId,
              followType = "TAG",
              postUserId = userId
            )
            _ <- notificationService.createFollowNotification(
              wordId = req.wordId,
              tagId = req.tagId,
              contentTypeId = "3", // Thread
              contentId = threadId,
              followType = "WORD",
              postUserId = userId
            )
            quote <- contentQuoteRepo.findForTarget(ContentQuoteTypes.Thread, threadId)
          } yield {
            val threadType = savedThread.threadTypeId match {
              case "1" => ThreadType("1", "Discussion")
              case "2" => ThreadType("2", "Question")
              case "3" => ThreadType("3", "Chat")
              case "4" => ThreadType("4", "Report")
              case _   => ThreadType(savedThread.threadTypeId, "Unknown")
            }

            Right(
              ThreadViewValue(
                threadId = threadId,
                userId = Some(userId), // ← 内部用：オーナーシップ確認
                displayId = maybeUser.map(_.displayId), // ← 公開用：画面表示
                userName = maybeUser.map(_.userName).getOrElse(userId),
                threadTitle = savedThread.threadTitle,
                threadCreateDate = savedThread.threadCreateDate,
                threadType = threadType,
                wordId = savedThread.wordId,
                tagId = savedThread.tagId,
                questionStatus = created.question.map(_ => QuestionStatusViewValue(false, None, None)),
                commentCount = 0,
                quote = quote
              )
            )
          }
        }
        .recover {
          case e: InvalidQuoteException => Left(ErrorResponse("400", e.reason))
          case e: Exception             => Left(ErrorResponse("500", s"Database error: ${e.getMessage}"))
        }
    }
  }

//  def findMyThreads(userId: String): Future[Seq[ThreadModel]] = {
//    threadRepo.findByUserId(userId)
//  }

//  def findMyComments(userId: String): Future[Seq[CommentModel]] = {
//    commentRepo.findByUserId(userId)
//  }

  // スレッドを削除する（論理削除）
  def deleteThread(threadId: Long, userId: String): Future[Either[ErrorResponse, Unit]] = {
    threadRepo.findById(threadId).flatMap {
      case Some(thread) if thread.userId.contains(userId) =>
        communityPostRepo
          .logicalDeleteThread(threadId, userId, System.currentTimeMillis())
          .map(_ => Right(()))
          .recover { case ex: Exception =>
            Left(ErrorResponse("500", s"スレッドの削除に失敗しました: ${ex.getMessage}"))
          }
      case Some(_) =>
        Future.successful(Left(ErrorResponse("403", "自分の投稿以外は削除できません")))
      case None =>
        Future.successful(Left(ErrorResponse("404", "スレッドが見つかりません")))
    }
  }

  // コメントを削除する（論理削除）
  def deleteComment(commentId: Long, userId: String): Future[Either[ErrorResponse, Unit]] = {
    commentRepo.findById(Some(commentId)).flatMap {
      case Some(comment) if comment.userId.contains(userId) =>
        communityPostRepo
          .logicalDeleteComment(commentId, userId, System.currentTimeMillis())
          .map { _ =>
            Right(())
          }
          .recover { case ex: Exception =>
            Left(ErrorResponse("500", s"コメントの削除に失敗しました: ${ex.getMessage}"))
          }
      case Some(_) =>
        Future.successful(Left(ErrorResponse("403", "自分の投稿以外は削除できません")))
      case None =>
        Future.successful(Left(ErrorResponse("404", "コメントが見つかりません")))
    }
  }

  // スレッドを編集する
  def updateThread(threadId: Long, newTitle: String, userId: String): Future[Either[ErrorResponse, ThreadViewValue]] = {
    val trimmedTitle = utils.Validation.trimAll(newTitle)
    if (!utils.Validation.notBlank(newTitle)) {
      Future.successful(Left(ErrorResponse("400", "タイトルに空白のみは使用できません")))
    } else if (!Validation.lengthVal(100, trimmedTitle)) {
      Future.successful(Left(ErrorResponse("400", "タイトルは100文字以内で入力してください")))
    } else {
      threadRepo.findById(threadId).flatMap {
        case Some(thread) if thread.userId.contains(userId) =>
          val now = System.currentTimeMillis()
          val updated = thread.copy(threadTitle = trimmedTitle, lastModified = now)
          threadRepo
            .update(updated)
            .flatMap { _ =>
              for {
                maybeUser <- userRepo.findById(userId)
                maybeQuestion <- threadQRepo.findByThreadId(threadId)
                maybeType <- threadTypeRepo.findById(thread.threadTypeId)
                quote <- contentQuoteRepo.findForTarget(ContentQuoteTypes.Thread, threadId)
              } yield {
                val threadType = maybeType.getOrElse(ThreadType(thread.threadTypeId, "Unknown"))
                val questionStatus = maybeQuestion.map { q =>
                  QuestionStatusViewValue(
                    isResolved = q.bestCommentId.isDefined,
                    resolvedDate = None,
                    bestCommentId = q.bestCommentId
                  )
                }
                Right(
                  ThreadViewValue(
                    threadId = threadId,
                    userId = thread.userId, // ← 内部用：オーナーシップ確認
                    displayId = maybeUser.map(_.displayId), // ← 公開用：画面表示
                    userName = maybeUser.map(_.userName).getOrElse(userId),
                    threadTitle = updated.threadTitle,
                    threadCreateDate = updated.threadCreateDate,
                    threadType = threadType,
                    wordId = thread.wordId, // ★ 追加
                    tagId = thread.tagId,
                    questionStatus = questionStatus,
                    commentCount = thread.threadCommentCount,
                    quote = quote
                  )
                )
              }
            }
            .recover { case ex: Exception =>
              Left(ErrorResponse("500", s"スレッドの更新に失敗しました: ${ex.getMessage}"))
            }
        case Some(_) =>
          Future.successful(Left(ErrorResponse("403", "自分の投稿以外は編集できません")))
        case None =>
          Future.successful(Left(ErrorResponse("404", "スレッドが見つかりません")))
      }
    }
  }

  // コメントを編集する
  def updateComment(
      commentId: Long,
      newText: String,
      userId: String
  ): Future[Either[ErrorResponse, CommentViewValue]] = {
    // 1. バリデーション (400文字制限)
    val trimmedText = utils.Validation.trimAll(newText)
    if (!utils.Validation.notBlank(newText)) {
      Future.successful(Left(ErrorResponse("400", "コメントに空白のみは使用できません")))
    } else if (!Validation.lengthVal(400, trimmedText)) {
      Future.successful(Left(ErrorResponse("400", "コメントは400文字以内で入力してください")))
    } else {
      // 2. コメントの存在と所有権を確認
      commentRepo.findById(Some(commentId)).flatMap {
        case Some(comment) if comment.userId.contains(userId) =>
          val now = System.currentTimeMillis()
          val updated = comment.copy(commentText = trimmedText, lastModified = now)

          // 3. データベース更新
          commentRepo
            .update(updated)
            .flatMap { _ =>
              for {
                maybeUser <- userRepo.findById(userId)
                maybeQuestion <- threadQRepo.findByThreadId(comment.threadId)
                quote <- contentQuoteRepo.findForTarget(ContentQuoteTypes.Comment, commentId)
              } yield {
                // ベストアンサーフラグを再判定
                val isBest = maybeQuestion.flatMap(_.bestCommentId).contains(commentId)

                Right(
                  CommentViewValue(
                    comment = updated,
                    userName = maybeUser.map(_.userName).getOrElse(userId),
                    displayId = maybeUser.map(_.displayId), // ← 追加
                    isBestAnswer = isBest,
                    // 編集直後はリアクション状態を保持するか、再取得が必要
                    // ここではViewValueの型に合わせて初期値を設定（フロント側でマージされる）
                    isGood = false,
                    isBad = false,
                    quote = quote
                  )
                )
              }
            }
            .recover { case ex: Exception =>
              Left(ErrorResponse("500", s"コメントの更新に失敗しました: ${ex.getMessage}"))
            }
        case Some(_) =>
          Future.successful(Left(ErrorResponse("403", "自分の投稿以外は編集できません")))
        case None =>
          Future.successful(Left(ErrorResponse("404", "コメントが見つかりません")))
      }
    }
  }

  def resolveThread(req: ResolveThreadRequest, userId: String): Future[Either[ErrorResponse, Unit]] = {
    // 1. スレッドが存在するか、かつそのスレッドの作成者が本人かを確認
    threadRepo.findById(req.threadId).flatMap {
      case Some(thread) if thread.userId.contains(userId) =>
        commentRepo.findById(Some(req.bestCommentId)).flatMap {
          case Some(comment) if comment.threadId == req.threadId && comment.userId.isDefined =>
            threadQRepo.resolve(req.threadId, req.bestCommentId).flatMap {
              case n if n > 0 =>
                notificationService
                  .createBestAnswerNotification(
                    threadId = req.threadId,
                    commentId = req.bestCommentId,
                    answerUserId = comment.userId.get,
                    selectedByUserId = userId
                  )
                  .map(_ => Right(()))
              case _ =>
                Future.successful(Left(ErrorResponse("404", "質問設定の更新に失敗しました")))
            }
          case Some(_) =>
            Future.successful(Left(ErrorResponse("400", "指定したコメントはこのスレッドの回答ではありません")))
          case None =>
            Future.successful(Left(ErrorResponse("404", "ベストアンサーに指定したコメントが見つかりません")))
        }

      case Some(_) =>
        Future.successful(Left(ErrorResponse("403", "自分のスレッド以外でベストアンサーは選べません")))

      case None =>
        Future.successful(Left(ErrorResponse("404", "スレッドが見つかりません")))
    }
  }
}
