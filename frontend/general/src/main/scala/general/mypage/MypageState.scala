package general.mypage

import com.raquo.laminar.api.L.Var
import models.MyProfileResponse

final class MypageState {
  val profileVar = Var(Option.empty[MyProfileResponse])
  val isFetchingVar = Var(false)
  val showPasswordModalVar = Var(false)
  val showDeleteModalVar = Var(false)
}
