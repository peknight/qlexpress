package com.peknight.qlexpress3.demo

import com.ql.util.express.IExpressContext
import org.springframework.context.ApplicationContext

import scala.jdk.CollectionConverters.*
import scala.util.{Failure, Success, Try}

// 与spring框架的无缝集成
case class QLExpressContext(map: Map[String, AnyRef], context: Option[ApplicationContext])
  extends java.util.HashMap[String, AnyRef](map.asJava)
    with IExpressContext[String, AnyRef]:
  override def get(key: Any): AnyRef =
    Try {
      (Option(super.get(key)), key, context) match
        case (Some(value: AnyRef), _, _) => Some(value)
        case (_, k: String, Some(ctx)) if ctx.containsBean(k) => Option(ctx.getBean(k))
        case _ => None
    } match
      case Success(value) => value.orNull
      case Failure(exception) => throw exception

  override def put(key: String, value: scala.Any): AnyRef =
    super.put(key, value.asInstanceOf[AnyRef])
end QLExpressContext
