package com.example.componentclient

import com.example.component.Facade

object ComponentClient {
  def value: String = Facade("root").render
}
