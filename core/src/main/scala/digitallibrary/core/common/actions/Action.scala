package digitallibrary.core.common.actions

import digitallibrary.core.auth.user.UserId

enum Action:
  case SetupNewUser(id: UserId)
