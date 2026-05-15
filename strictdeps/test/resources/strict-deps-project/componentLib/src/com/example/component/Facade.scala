package com.example.component

final case class Facade(label: String) {
  def render: String = Facade.component(this)
}

object Facade {
  private final class Backend {
    def render(props: Facade): String = ChildWrapper.render(props.label)
  }

  private val backend = new Backend

  def component(props: Facade): String = backend.render(props)
}
