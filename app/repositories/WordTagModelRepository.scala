package repositories
import models.db.{TagModel, Word, WordTagModel}
import models.db.TableQueries

import models.*
import models.db.TableQueries.wordTags
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import javax.inject.*
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class WordTagModelRepository @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(implicit
    ec: ExecutionContext
) extends HasDatabaseConfigProvider[JdbcProfile] {

  import profile.api.*

  // LIKE句の特殊文字をエスケープする (クラス内に移動)
  private def escapeLikePattern(str: String): String = {
    str.flatMap {
      case '\\' => "\\\\"
      case '%'  => "\\%"
      case '_'  => "\\_"
      case c    => c.toString
    }
  }

  /** 全データを取得する */
  def listAll(): Future[Seq[WordTagModel]] = {
    db.run(wordTags.result)
  }

  // WordTagModelRepository.scala
  def findWordsByMultipleKeywords(keywords: Seq[String]): Future[Seq[((WordTagModel, Word), TagModel)]] = {
    val query = wordTags
      .join(TableQueries.words)
      .on(_.wordId === _.wordId)
      .join(TableQueries.tags)
      .on(_._1.tagId === _.tagId)
      .filter { case ((wt, w), t) =>
        // 全てのキーワードが含まれているか (AND検索)
        keywords
          .take(5)
          .map { kw => // 最大5キーワードまで制限
            val escaped = escapeLikePattern(kw.take(50)) // 各キーワードを50文字に制限
            (w.wordMain.toLowerCase like s"%${escaped.toLowerCase}%") ||
            (t.tagName.toLowerCase like s"%${escaped.toLowerCase}%") || // tag -> t に修正
            (wt.wordReading.toLowerCase like s"%${escaped.toLowerCase}%") // escape句を削除
          }
          .reduceLeft((a, b) => a && b) // 明示的なラムダ式に変更
      }

    db.run(query.result)
  }

  /** 特定のwordIdやtagIdで絞り込む (片方のみでも動作) */
  def searchBySpecificIds(wordId: Option[Long], tagId: Option[Long]): Future[Seq[((WordTagModel, Word), TagModel)]] = {
    val query = wordTags
      .join(TableQueries.words)
      .on(_.wordId === _.wordId)
      .join(TableQueries.tags)
      .on(_._1.tagId === _.tagId)
      .filterOpt(wordId)((row, id) => row._1._1.wordId === id)
      .filterOpt(tagId)((row, id) => row._1._1.tagId === id)

    db.run(query.result)
  }

  def findByTagNamesAndKeywords(
      tagName: String,
      keywords: Seq[String]
  ): Future[Seq[((WordTagModel, Word), TagModel)]] = {
    // 1. 基本となる結合クエリ
    val baseQuery = wordTags
      .join(TableQueries.words)
      .on(_.wordId === _.wordId)
      .join(TableQueries.tags)
      .on(_._1.tagId === _.tagId)
      .filter { case ((wt, w), t) => t.tagName === tagName } // まず指定されたタグで絞り込み

    // 2. 追加のキーワード（キーワード1 AND キーワード2...）を動的に適用
    val finalQuery = if (keywords.isEmpty) {
      baseQuery
    } else {
      baseQuery.filter { case ((wt, w), t) =>
        keywords
          .take(5)
          .map { kw => // 最大5キーワードまで制限
            val escaped = escapeLikePattern(kw.take(50)) // 各キーワードを50文字に制限
            (w.wordMain.toLowerCase like s"%${escaped.toLowerCase}%") || // escape句を削除
            (wt.wordReading.toLowerCase like s"%${escaped.toLowerCase}%") // escape句を削除
          }
          .reduceLeft((a, b) => a && b) // 明示的なラムダ式に変更
      }
    }

    db.run(finalQuery.result)
  }

  def findByTagName(tagName: String): Future[Seq[(WordTagModel, Word, TagModel)]] = {
    // 入力長の制限
    if (tagName.length > 50) {
      return Future.successful(Nil)
    }

    val escaped = escapeLikePattern(tagName)
    val query = wordTags
      .join(TableQueries.tags)
      .on(_.tagId === _.tagId)
      .join(TableQueries.words)
      .on(_._1.wordId === _.wordId)
      .filter { case ((wt, tag), word) =>
        tag.tagName === tagName || (tag.tagName.toLowerCase like s"%${escaped.toLowerCase}%") // escape句を削除
      }
      .map { case ((wt, tag), word) => (wt, word, tag) }

    db.run(query.result)
  }

  def findRelatedWordsByWordId(wordId: Long): Future[Seq[(WordTagModel, Word)]] = {
    // 1. まず、その言葉(wordId)が付いているタグのIDをすべて取得するクエリ
    val relevantTagIds = wordTags.filter(_.wordId === wordId).map(_.tagId)

    // 2. それらのタグIDのいずれかを持っていて、かつ自分自身(wordId)ではない言葉を検索
    val query = wordTags
      .filter(wt => wt.tagId.in(relevantTagIds) && wt.wordId =!= wordId)
      .join(TableQueries.words)
      .on(_.wordId === _.wordId)

    db.run(query.result)
  }

  /** wordIdが一致するwordtag+tagを取得する */
  def findByWordId(wordId: Long, number: Option[Int] = None): Future[Seq[(WordTagModel, TagModel)]] = {
    val baseQuery = wordTags
      .join(TableQueries.tags)
      .on(_.tagId === _.tagId)
      .filter { case (wt, _) => wt.wordId === wordId }
      .sortBy { case (wt, t) => (t.tagName, wt.wordCreateDate.desc) }

    val query = number match {
      case Some(n) => baseQuery.take(n)
      case None    => baseQuery
    }

    db.run(query.result).map { results =>
      // タグ名でグループ化して、各グループの最初の1件だけ取得
      results.groupBy(_._2.tagName).values.flatMap(_.headOption).toSeq.take(number.getOrElse(100))
    }
  }

  /** tagIdが一致するwordtag+wordを取得する */
  def findByTagId(tagId: Long): Future[Seq[(WordTagModel, Word)]] = {
    db.run(
      wordTags
        .join(TableQueries.words)
        .on(_.wordId === _.wordId)
        .filter { case (wt, _) => wt.tagId === tagId }
        .sortBy { case (wt, w) => (w.wordMain, wt.wordCreateDate.desc) }
        .result
    ).map { results =>
      // 単語名でグループ化して、各グループの最初の1件だけ取得
      results.groupBy(_._2.wordMain).values.flatMap(_.headOption).toSeq
    }
  }

  /** 単語名の部分一致検索（単語名でグループ化して重複除去） */
  def findWordsByPartialName(q: String): Future[Seq[(WordTagModel, Word)]] = {
    if (q.length > 50) {
      return Future.successful(Nil)
    }
    val escaped = escapeLikePattern(q)
    db.run(
      wordTags
        .join(TableQueries.words)
        .on(_.wordId === _.wordId)
        .filter { case (wt, w) => w.wordMain.toLowerCase like s"%${escaped.toLowerCase}%" }
        .sortBy { case (wt, w) => (w.wordMain, wt.wordCreateDate.desc) }
        .take(50)
        .result
    ).map { results =>
      // 単語名でグループ化して、各グループの最初の1件だけ取得
      results.groupBy(_._2.wordMain).values.flatMap(_.headOption).toSeq.take(10)
    }
  }

  /** タグ名の部分一致検索（タグ名でグループ化して重複除去） */
  def findTagsByPartialName(q: String): Future[Seq[(WordTagModel, TagModel)]] = {
    if (q.length > 50) {
      return Future.successful(Nil)
    }
    val escaped = escapeLikePattern(q)
    db.run(
      wordTags
        .join(TableQueries.tags)
        .on(_.tagId === _.tagId)
        .filter { case (wt, t) => t.tagName.toLowerCase like s"%${escaped.toLowerCase}%" }
        .sortBy { case (wt, t) => (t.tagName, wt.wordCreateDate.desc) }
        .take(50)
        .result
    ).map { results =>
      // タグ名でグループ化して、各グループの最初の1件だけ取得
      results.groupBy(_._2.tagName).values.flatMap(_.headOption).toSeq.take(10)
    }
  }

  /** IDでデータを1つ取得する */
  def findByWordTagId(wordId: Long, tagId: Long): Future[Option[(WordTagModel, Word, TagModel)]] = {
    db.run(
      wordTags
        .join(TableQueries.words)
        .on(_.wordId === _.wordId)
        .join(TableQueries.tags)
        .on(_._1.tagId === _.tagId)
        .filter { case ((wt, _), _) =>
          wt.wordId === wordId && wt.tagId === tagId
        }
        .map { case ((wt, w), t) => (wt, w, t) }
        .result
        .headOption
    )
  }

  def findByUserId(userId: String): Future[Seq[(WordTagModel, Word)]] = {
    db.run(
      wordTags
        .join(TableQueries.words)
        .on(_.wordId === _.wordId)
        .filter { case (wt, _) => wt.userId === userId }
        .result
    )
  }

  def findByUserIdWithWordTag(userId: String): Future[Seq[(WordTagModel, Word, TagModel)]] = {
    db.run(
      wordTags
        .join(TableQueries.words)
        .on(_.wordId === _.wordId)
        .join(TableQueries.tags)
        .on(_._1.tagId === _.tagId)
        .filter { case ((wt, _), _) => wt.userId === userId }
        .sortBy { case ((wt, w), t) => wt.wordCreateDate.desc }
        .map { case ((wt, w), t) => (wt, w, t) }
        .result
    )
  }

  /** wordIdが一致するWordTagをすべて取得する */
  def findWordsByKeyword(keyword: Option[String]): Future[Seq[(WordTagModel, Option[Word], Option[TagModel])]] = {
    val searchKeyword = s"%${keyword.getOrElse("")}%"

    val query = wordTags
      .joinLeft(TableQueries.words)
      .on(_.wordId === _.wordId)
      .joinLeft(TableQueries.tags)
      .on(_._1.tagId === _.tagId)
      .joinLeft(TableQueries.meanings)
      .on(_._1._1.wordId === _.wordId)
      .joinLeft(TableQueries.examples)
      .on(_._1._1._1.wordId === _.wordId)
      .filter { case ((((wt, w), t), m), e) =>
        w.map(_.wordMain like searchKeyword.bind).getOrElse(false: Rep[Boolean]) ||
        m.map(_.meaningText like searchKeyword.bind).getOrElse(false: Rep[Boolean]) ||
        e.map(_.exampleText like searchKeyword.bind).getOrElse(false: Rep[Boolean]) ||
        t.map(_.tagName like searchKeyword.bind).getOrElse(false: Rep[Boolean]) ||
        (wt.wordReading like searchKeyword.bind)
      }
      .map { case ((((wt, w), t), m), e) => (wt, w, t) }
      .distinct

    db.run(query.result)
  }

  /** データを新規登録する */
  def create(wordTag: WordTagModel): Future[Int] = {
    db.run(wordTags += wordTag)
  }

  /** データ情報を更新する */
  def update(wordTag: WordTagModel): Future[Int] = {
    db.run(
      wordTags
        .filter(r => r.wordId === wordTag.wordId && r.tagId === wordTag.tagId)
        .update(wordTag)
    )
  }

  /** データを削除する */
  def delete(wordId: Long, tagId: Long): Future[Int] = {
    db.run(
      wordTags
        .filter(r => r.wordId === wordId && r.tagId === tagId)
        .delete
    )
  }

  def saveWordTag(word: WordTagModel): Future[WordTagModel] = {
    db.run(wordTags += word).map { _ =>
      word
    }
  }

  /** 既存なら更新(または維持)、なければ新規登録する */
  def upsert(wordTag: WordTagModel): Future[WordTagModel] = {
    db.run(wordTags.insertOrUpdate(wordTag)).map(_ => wordTag)
  }

  /** ランダムに指定件数のWordTagを取得する (IDベース) */
  def getRandomWordTags(limit: Int): Future[Seq[(WordTagModel, Option[Word], Option[TagModel])]] = {
    // 1. まず最小IDと最大IDを取得
    val minQuery = wordTags.map(_.wordId).min.result
    val maxQuery = wordTags.map(_.wordId).max.result

    db.run(minQuery.zip(maxQuery)).flatMap { case (minOpt, maxOpt) =>
      (minOpt, maxOpt) match {
        case (Some(min), Some(max)) =>
          // 2. 重複のないランダムなID群を効率的に生成
          val range = max - min + 1
          val randomIds = scala.collection.mutable.Set[Long]()
          while (randomIds.size < Math.min(limit * 10, range.toInt)) {
            randomIds += min + scala.util.Random.nextLong(range)
          }

          // 3. そのIDに該当する行を取得
          val query = wordTags
            .joinLeft(TableQueries.words)
            .on(_.wordId === _.wordId)
            .joinLeft(TableQueries.tags)
            .on(_._1.tagId === _.tagId)
            .filter { case ((wt, w), t) => wt.wordId.inSet(randomIds.toSeq) }
            .map { case ((wt, w), t) => (wt, w, t) }

          db.run(query.result).map { results =>
            // 結果をシャッフルしてlimit件取得（重複なし保証）
            scala.util.Random.shuffle(results).take(limit)
          }

        case _ =>
          // データが空の場合
          Future.successful(Seq.empty)
      }
    }
  }
}
