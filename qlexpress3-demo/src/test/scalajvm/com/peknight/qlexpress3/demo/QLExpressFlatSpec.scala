package com.peknight.qlexpress3.demo

import com.peknight.error.Error
import com.peknight.qlexpress3.demo.{BeanExample, CustomBean, DemoMethods, OrderQuery, RiskBean}
import com.peknight.validation.std.either.typed
import com.ql.util.express.*
import com.ql.util.express.config.QLExpressRunStrategy
import com.ql.util.express.config.whitelist.CheckerFactory
import com.ql.util.express.instruction.op.OperatorBase
import org.scalatest.flatspec.AnyFlatSpec

import java.util.Date
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*
import scala.reflect.ClassTag
import scala.util.Try

class QLExpressFlatSpec extends AnyFlatSpec:
  private def run[A](
    express: String,
    context: Map[String, Any] = Map.empty,
    precise: Boolean = true,
    trace: Boolean = false,
    cache: Boolean = true,
    timeout: Duration = -1.millis,
    consumer: ExpressRunner => Unit = (_: ExpressRunner) => ()
  )(using classTag: ClassTag[A]): Either[Error, A] =
    val runner = new ExpressRunner(precise, trace)
    consumer(runner)
    val ctx = new DefaultContext[String, Any]()
    ctx.putAll(context.asJava)
    Try(runner.execute(express, ctx, null, cache, trace, timeout.toMillis))
      .toEither.left.map(Error.apply)
      .flatMap(value => typed[A](Option(value).fold(())(identity)))
  end run

  "QL Express 调用依赖和说明" should "pass" in {
    assert(run[Int]("a + b * c", Map("a" -> 1, "b" -> 2, "c" -> 3)).contains(7))
  }

  "QL Express 普通Java语法" should "pass" in {
    assert(run[Int](
      """
        |n = 10;
        |sum = 0;
        |for (i = 0; i < n; i++) {
        |    sum = sum + 1;
        |}
        |return sum;
      """.stripMargin
    ).contains(10))
  }

  "QLExpress 逻辑三元操作" should "pass" in {
    assert(run[Int](
      """
        |a = 1;
        |b = 2;
        |maxnum = a > b ? a : b
      """.stripMargin
    ).contains(2))
  }

  "QLExpress Java语法" should "pass" in {
    assert(run[Array[Integer]](
      """
        |keys = new ArrayList();
        |deviceName2Value = new HashMap();
        |deviceNames = ["ng", "si", "umid", "ut", "mac", "imsi", "imei"];
        |mins = [5, 30];
    """.stripMargin
    ).map(_.toList).contains(List(5, 30)))
  }

  "QLExpress 对象类型声明" should "pass" in {
    case class FocFulfillDecisionReqDTO(value: Int = 7)
    case class Param(reqDTO: FocFulfillDecisionReqDTO = FocFulfillDecisionReqDTO())
    assert(run[FocFulfillDecisionReqDTO](
      """
        |reqDTO = param.reqDTO();
      """.stripMargin,
      Map("param" -> Param())
    ).contains(FocFulfillDecisionReqDTO()))
  }

  "QLExpress 数组遍历" should "pass" in {
    assert(run[Int](
      """
        |sum = 0;
        |for (i = 0; i < list.size(); i++) {
        |    item = list.get(i);
        |    sum = sum + item;
        |}
        |return sum;
      """.stripMargin,
      Map("list" -> List(1, 2, 3).asJava)
    ).contains(6))
  }

  "QLExpress Map遍历" should "pass" in {
    assert(run[Unit](
      """
        |keySet = map.keySet();
        |objArr = keySet.toArray();
        |for (i = 0; i < objArr.length; i++) {
        |    key = objArr[i];
        |    System.out.println(map.get(key));
        |}
      """.stripMargin,
      Map("map" -> Map("a" -> 3, "b" -> 2, "c" -> 1).asJava)
    ).contains(()))
  }

  "QLExpress Java的对象操作" should "pass" in {
    case class Result(id: Long)
    class BizOrderDAO:
      def query(orderQuery: OrderQuery): Result = Result(123L)
    end BizOrderDAO
    assert(run[Unit](
      """
        |import com.peknight.qlexpress3.demo.OrderQuery;
        |query = new OrderQuery();
        |query.setCreateDate(new Date());
        |query.setBuyer("张三");
        |result = bizOrderDAO.query(query);
        |System.out.println(result.id());
      """.stripMargin,
      Map("bizOrderDAO" -> new BizOrderDAO)
    ).contains(()))
  }

  "QLExpress 脚本中定义function" should "pass" in {
    assert(run[Int](
      """
        |function add(int a, int b) {
        |    return a + b;
        |};
        |function sub(int a, int b) {
        |    return a - b;
        |};
        |a = 10;
        |return add(a, 4) + sub(a, 9)
    """.stripMargin
    ).contains(15))
  }

  "QLExpress 扩展操作符" should "pass" in {
    assert(run[Int](
      """
        |如果 (语文 + 数学 + 英语 > 270) 则 { return 1; } 否则 { return 0; }
      """.stripMargin,
      Map("语文" -> 99, "数学" -> 100, "英语" -> 80),
      consumer = runner => {
        runner.addOperatorWithAlias("如果", "if", null)
        runner.addOperatorWithAlias("则", "then", null)
        runner.addOperatorWithAlias("否则", "else", null)
      }
    ).contains(1))
  }

  class JoinOperator extends Operator:
    def executeInner(list: Array[AnyRef]): AnyRef =
      list.toList.foldLeft(List.empty[AnyRef]) {
        case (acc, current: java.util.List[?]) => acc ::: current.asScala.toList
        case (acc, current) => acc :+ current
      }.asJava
  end JoinOperator

  "QLExpress 自定义Operator" should "pass" in {
    assert(run[java.util.List[Integer]]("1 join 2 join 3", consumer = _.addOperator("join", new JoinOperator))
      .map(_.asScala.toList).contains(List(1, 2, 3)))
    assert(run[java.util.List[Integer]]("1 + 2 + 3", consumer = _.replaceOperator("+", new JoinOperator))
      .map(_.asScala.toList).contains(List(1, 2, 3)))
    assert(run[java.util.List[Integer]]("join(1, 2, 3)", consumer = _.addFunction("join", new JoinOperator))
      .map(_.asScala.toList).contains(List(1, 2, 3)))
  }

  "QLExpress 绑定Java类或者对象的method" should "pass" in {
    assert(run[Boolean](
      """
        |System.out.println(取绝对值(-100));
        |打印(转换大小写("hello world"));
        |打印("你好吗？");
        |contains("helloworld", "aeiou");
      """.stripMargin,
      consumer = runner => {
        runner.addFunctionOfClassMethod("取绝对值", classOf[Math].getName, "abs", Array[String]("double"), null)
        runner.addFunctionOfClassMethod("转换大小写", classOf[BeanExample].getName, "upper", Array[String]("String"), null)
        runner.addFunctionOfServiceMethod("打印", System.out, "println", Array[String]("String"), null)
        runner.addFunctionOfServiceMethod("contains", new BeanExample, "anyContains", Array[Class[?]](classOf[String],
          classOf[String]), null)
      }
    ).contains(true))
  }

  "QLExpress macro宏定义" should "pass" in {
    assert(run[Boolean]("是否优秀",
      Map("语文" -> 88, "数学" -> 99, "英语" -> 95),
      consumer = runner => {
        runner.addMacro("计算平均成绩", "(语文+数学+英语)/3.0")
        runner.addMacro("是否优秀", "计算平均成绩>90")
      }
    ).contains(true))
  }

  "QLExpress 编译脚本查询外部需要定义的变量和函数" should "pass" in {
    val baseVarNames = Set("语文", "数学", "英语", "综合考试")
    val allVarNames = baseVarNames + "平均分"
    // 注意以下脚本有int和没有int的区别
    val outVarExpress = "int 平均分 = (语文 + 数学 + 英语 + 综合考试.科目2) / 4.0; return 平均分"
    val outVarNames = new ExpressRunner().getOutVarNames(outVarExpress)
    assert(baseVarNames.forall(outVarNames.contains))
    assert(outVarNames.forall(baseVarNames.contains))
    val outVarExpressWithoutInt = "平均分 = (语文 + 数学 + 英语 + 综合考试.科目2) / 4.0; return 平均分"
    val outVarNamesWithoutInt = new ExpressRunner().getOutVarNames(outVarExpressWithoutInt)
    assert(allVarNames.forall(outVarNamesWithoutInt.contains))
    assert(outVarNamesWithoutInt.forall(allVarNames.contains))
  }

  "QLExpress 关于不定参数的使用" should "pass" in {
    // 默认的不定参数可以使用数组来代替
    assert(run[String](
      "getTemplate([11, '22', 33L, true])",
      consumer = _.addFunctionOfServiceMethod("getTemplate", new DemoMethods, "getTemplate",
        Array[Class[?]](classOf[Array[AnyRef]]), null)
    ).contains("11,22,33,true,"))

    DynamicParamsUtil.supportDynamicParams = true
    assert(run[String](
      "getTemplate(11, '22', 33L, true)",
      consumer = _.addFunctionOfServiceMethod("getTemplate", new DemoMethods, "getTemplate",
          Array[Class[?]](classOf[Array[AnyRef]]), null)
    ).contains("11,22,33,true,"))
    DynamicParamsUtil.supportDynamicParams = false
  }

  "QLExpress 关于集合的快捷写法" should "pass" in {
    assert(run[Int]("abc = NewMap(1:1, 2:2); return abc.get(1) + abc.get(2);").contains(3))
    assert(run[Int]("abc = NewList(1, 2, 3); return abc.get(1) + abc.get(2);").contains(5))
    assert(run[Int]("abc = [1, 2, 3]; return abc[1] + abc[2];").contains(5))
  }

  "QLExpress 集合的遍历" should "pass" in {
    // VM options add:
    // --add-opens=java.base/java.util=ALL-UNNAMED
    assert(run[Unit](
      """
        |map = new HashMap();
        |map.put("a", "a_value");
        |map.put("b", "b_value");
        |keySet = map.keySet();
        |objArr = keySet.toArray();
        |for (i = 0; i < objArr.length; i++) {
        |    key = objArr[i];
        |    System.out.println(map.get(key));
        |}
      """.stripMargin
    ).contains(()))
  }

  "QLExpress 功能扩展API列表" should "pass" in {
    assert(run[java.util.List[Integer]](
      """
        |list = 1 join 2 join 3;
        |list = joinF(list, 4, 5, 6);
      """.stripMargin,
      consumer = runner => {
        runner.addOperator("join", new JoinOperator)
        runner.addFunction("joinF", new JoinOperator)
      }
    ).map(_.asScala.toList).contains(List(1, 2, 3, 4, 5, 6)))
  }

  "QLExpress Java Class的相关api" should "pass" in {
    class ListLengthOperator extends Operator:
      override def executeInner(list: Array[AnyRef]): Int =
        list.head.asInstanceOf[java.util.List[?]].size()
    end ListLengthOperator
    assert(run[java.util.List[String]](
      """
        |list = new ArrayList();
        |list.add("1");
        |list.add("2");
        |list.add("3");
        |System.out.println(list.长度);
        |list.join("4").join("5");
        |""".stripMargin,
      consumer = runner => {
        runner.addClassField("长度", classOf[java.util.List[?]], classOf[Int], new ListLengthOperator)
        runner.addClassMethod("join", classOf[java.util.List[?]], new JoinOperator)
      }
    ).map(_.asScala.toList).contains(List("1", "2", "3", "4", "5")))
  }

  "QLExpress 语法树解析变量、函数的API getOutVarNames getOutFunctionNames" should "pass" in {
    val functionNames = Set("fun1", "fun2")
    val outFunctionExpress = "a + fun1(a) + fun2(a + b) + c.getName()"
    val outFunctionNames = new ExpressRunner().getOutFunctionNames(outFunctionExpress)
    assert(functionNames.forall(outFunctionNames.contains))
    assert(outFunctionNames.forall(functionNames.contains))
  }

  "QLExpress 语法解析校验api" should "pass" in {
    val runner = new ExpressRunner()
    val parseInstructionSetExpress = "for (i = 0; i < 10; i++) { sum = i + 1; } return sum;"
    val instructionSet = runner.parseInstructionSet(parseInstructionSetExpress)
    // 优先从本地指令集缓存获取指令集，没有的话生成并且缓存在本地
    val parseInstructionSetCache = runner.getInstructionSetFromLocalCache(parseInstructionSetExpress)
    // 清除缓存
    runner.clearExpressCache()
  }

  "QLExpress 安全风险控制" should "pass" in {
    // 防止死循环
    assert(run[Int](
      """
        |sum = 0;
        |for (i = 0; i < 1000000000; i++) {
        |    sum = sum + i;
        |}
        |return sum;
      """.stripMargin,
      timeout = 1.second
    ).isLeft)
  }

  "QLExpress 防止调用不安全的系统api" should "pass" in {
    // QLExpressRunStrategy.setForbidInvokeSecurityRiskMethods(true)
    assert(run[Unit]("System.exit(1);").isLeft)
  }

  "QLExpress 自定义函数操作符获取原始的context控制上下文" should "pass" in {
    class OperatorContextPut(name: String) extends OperatorBase:
      def executeInner(parent: InstructionSetContext, list: ArraySwap): OperateData =
        val key = list.get(0).toString
        val value = list.get(1)
        parent.put(key, value)
        null
    end OperatorContextPut
    assert(run[Unit](
      """
        |contextPut('success', 'false');
        |contextPut('error', '错误信息');
        |contextPut('warning', '提醒信息');
      """.stripMargin,
      Map("success" -> "true"),
      consumer = _.addFunction("contextPut", OperatorContextPut("contextPut"))
    ).contains(()))
  }

  "QLExpress 多级别安全控制 黑名单控制" should "pass" in {
    /*
     * QLExpress目前默认添加的黑名单有:
     * java.lang.System.ext
     * java.lang.Runtime.exec
     * java.lang.ProcessBuilder.start
     * java.lang.reflect.Method.invoke
     * java.lang.reflect.Class.forName
     * java.lang.reflect.ClassLoader.loadClass
     * java.lang.reflect.ClassLoader.findClass
     */
    // 必须将该选项设置为true
    // QLExpressRunStrategy.setForbidInvokeSecurityRiskMethods(true)
    // 不区分静态方法与成员方法，写法一致
    // 不支持重载，riskMethod所有重载方法都会被禁止
    QLExpressRunStrategy.addSecurityRiskMethod(classOf[RiskBean], "riskMethod")
    assert(run[Unit](
      """
        |import com.peknight.qlexpress3.demo.RiskBean;
        |RiskBean.riskMethod()
      """.stripMargin
    ).isLeft)
  }

  "QLExpress 多级别安全控制 编译期白名单控制" should "pass" in {
    // 白名单控制，有白名单设置则所有的黑名单设置都会无效，以白名单为准。默认没有白名单设置
    // 编译期白名单无法检测运行时出现的类型，可能通过各种反射等绕过，需要接收终端用户输入的情况要配置运行期白名单
    // 设置编译期白名单
    QLExpressRunStrategy.setCompileWhiteCheckerList(List(
      // 精确设置
      CheckerFactory.must(classOf[Date]),
      // 子类设置
      CheckerFactory.assignable(classOf[java.util.List[?]])
    ).asJava)
    assert(run[Date]("new Date();").isRight)
    assert(run[java.util.LinkedList[Integer]](
      """
        |LinkedList ll = new LinkedList;
        |ll.add(1);
        |ll.add(2);
        |ll
      """.stripMargin
    ).isRight)
    assert(run[Unit]("String a = 'mmm'").isLeft)
    // Math不在白名单中，对于不满足编译期白名单的脚本无需运行，即可通过checkSyntax检测出
    assert(!new ExpressRunner().checkSyntax("Math.abs(-1)"))
    // 取消编译期白名单设置
    QLExpressRunStrategy.setCompileWhiteCheckerList(null)
    assert(new ExpressRunner().checkSyntax("Math.abs(-1)"))
  }

  "QLExpress 多级别安全控制 运行期白名单控制" should "pass" in {
    // 必须将该选项设置为 true
    // QLExpressRunStrategy.setForbidInvokeSecurityRiskMethods(true)
    // 有白名单设置时
    QLExpressRunStrategy.addSecureMethod(classOf[RiskBean], "secureMethod")
    assert(run[Unit](
      """
        |import com.peknight.qlexpress3.demo.RiskBean;
        |RiskBean.secureMethod()
      """.stripMargin
    ).contains(()))
    assert(run[Int]("'abcd'.length()").isLeft)
    val secureMethods = Set("java.lang.String.length", "java.lang.Integer.valueOf").asJava
    QLExpressRunStrategy.setSecureMethods(secureMethods)
    assert(run[Int]("Integer.valueOf('abcd'.length())").contains(4))
    assert(run[Long]("Long.valueOf('abcd'.length())").isLeft)
    // 取消运行期白名单设置
    QLExpressRunStrategy.setSecureMethods(new java.util.HashSet[String]())
    assert(run[Int]("'abcd'.length()").contains(4))
  }

  "QLExpress 多级别安全控制 沙箱模式" should "pass" in {
    QLExpressRunStrategy.setSandBoxMode(true)
    val runner = new ExpressRunner()
    // 沙箱模式下不支持import语句
    assert(!runner.checkSyntax("import com.peknight.qlexpress3.demo.RiskBean;"))
    // 沙箱模式下不支持显式的类型引用
    assert(!runner.checkSyntax("String a = 'abc'"))
    assert(runner.checkSyntax("a = 'abc'"))
    // 没有找到方法length
    assert(run[Int]("'abc'.length()").isLeft)
    // 无法获取属性id
    assert(run[Int]("test.getId()", Map("test" -> CustomBean(12))).isLeft)

    // 沙箱模式下可以使用 自定义操作符/宏/函数 和应用进行交互
    val addOperator = new Operator:
      override def executeInner(list: Array[AnyRef]): AnyRef =
        (list(0).asInstanceOf[Int] + list(1).asInstanceOf[Int]).asInstanceOf
    end addOperator
    assert(run[Int]("add(1, 2)", consumer = _.addFunction("add", addOperator)).contains(3))
    assert(run[String]("test.a", Map("test" -> Map("a" -> "t").asJava)).contains("t"))

    // 限制最大申请数组长度为10，默认没有限制，测试下来这个语法不对，没验证到
    val code = "byte[] a = new byte[11];"
    QLExpressRunStrategy.setMaxArrLength(10)
    assert(run[Unit](code, timeout = 20.millis).isLeft)
    QLExpressRunStrategy.setMaxArrLength(-1)
    assert(run[Int](code, timeout = 20.millis).isLeft)
  }
end QLExpressFlatSpec
