package shared.api

import upickle.default.ReadWriter

case class ReactionToggleRequest(
  contentTypeId: String,
  contentId: Long,
  reactionType: String
) derives ReadWriter

case class ReactionToggleResponse(active: Boolean, count: Int) derives ReadWriter

case class CommentReactionToggleResponse(
  active: Boolean,
  goodCount: Int,
  badCount: Int
) derives ReadWriter
