package models

import upickle.default.ReadWriter

case class Word(wordId: Option[Long], wordMain: String) derives ReadWriter
case class TagModel(tagId: Option[Long], tagName: String) derives ReadWriter
case class WordTagModel(wordId: Long, tagId: Long, userId: Option[String], wordCreateDate: Long, wordReading: String)
    derives ReadWriter
case class WordTagDetailResponse(
    wordId: Long,
    wordMain: String,
    tagId: Long,
    tagName: String,
    wordReading: String,
    isFollowed: Boolean
) derives ReadWriter
