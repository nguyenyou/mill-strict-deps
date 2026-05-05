package com.example.app

import com.example.api.Api
import com.example.domain.User

object App {
  val value: String = Api.label(User("Ada"))
}

