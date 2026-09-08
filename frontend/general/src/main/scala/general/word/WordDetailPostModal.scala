package general.word

import com.raquo.laminar.api.L.*
import general.word.DetailPostForm

object WordDetailPostModal {
  def apply(
      titleText: String,
      postType: DetailPostForm.PostType,
      draftContext: String,
      post: (DetailPostForm.PostPayload, () => Unit) => Unit,
      close: () => Unit
  ): HtmlElement =
    div(
      cls := "absolute inset-0 z-[100] flex items-center justify-center overflow-hidden p-4",
      div(
        cls := "absolute inset-0 bg-black/50 backdrop-blur-sm transition-opacity",
        onClick --> (_ => close())
      ),
      div(
        cls := "relative w-full max-w-md bg-white dark:bg-gray-900 rounded-[2rem] p-8 shadow-2xl animate-in fade-in zoom-in-95 duration-200 border border-transparent dark:border-gray-700",
        h3(
          cls := "text-xl font-black text-gray-900 dark:text-gray-100 mb-6 flex items-center justify-between",
          titleText,
          button(
            cls := "text-gray-400 hover:text-gray-600 dark:hover:text-gray-200",
            "×",
            onClick --> (_ => close())
          )
        ),
        DetailPostForm(
          postType = postType,
          draftContext = draftContext,
          onPost = post,
          onCancel = close
        )
      )
    )
}
