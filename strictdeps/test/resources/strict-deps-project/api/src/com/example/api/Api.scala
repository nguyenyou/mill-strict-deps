package com.example.api

import com.example.domain.User

object Api {
  def label(user: User): String = user.name
}

