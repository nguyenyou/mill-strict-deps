package com.example.app

import com.example.api.Api
import com.example.domain.User
import com.example.helper.ScalaHelper

object App {
  val value: String = Api.label(User("Ada")) + ScalaHelper.message
}
