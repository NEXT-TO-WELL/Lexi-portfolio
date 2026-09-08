package general.app

import com.raquo.laminar.api.L.*

object GlobalStyles {
  val element: HtmlElement = styleTag(
    """
      html, body {
        height: 100dvh !important;
        width: 100% !important;
        overflow: hidden !important;
        position: fixed !important;
        margin: 0;
        padding: 0;
        -webkit-overflow-scrolling: touch;
        cursor: default;
      }
      #app {
        height: 100dvh !important;
        width: 100%;
        overflow: hidden;
      }
      @media (min-width: 768px) {
        html, body {
          height: auto !important;
          min-height: 100%;
          overflow-x: hidden !important;
          overflow-y: auto !important;
          position: static !important;
        }
        #app {
          height: auto !important;
          min-height: 100dvh;
          overflow: visible !important;
        }
        #app > div > div {
          height: auto !important;
          min-height: 100dvh;
          overflow: visible !important;
        }
        #app main {
          overflow: visible !important;
        }
      }
    """
  )
}
