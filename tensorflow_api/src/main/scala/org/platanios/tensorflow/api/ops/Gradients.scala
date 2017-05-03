//package org.platanios.tensorflow.api.ops
//
//import org.platanios.tensorflow.api.Exception.InvalidDataTypeException
//import org.platanios.tensorflow.api.types.DataType
//
//import scala.collection.mutable
//
///**
//  * @author Emmanouil Antonios Platanios
//  */
//object Gradients {
//  /** Returns a boolean value indicating whether the data type of `tensor` is trainable. This means whether its
//    * gradients can be computed. */
//  private[this] def isTrainable(tensor: Op.Output): Boolean = {
//    Set[DataType](DataType.Float32, DataType.Float64).contains(tensor.dataType)
//    // TODO: !! FLOAT16, COMPLEX64, COMPLEX128.
//  }
//
//  /** Gathers and returns all inputs of `destinations` (recursively) that have been reached.
//    *
//    * @param  destinations Ops whose inputs are being gathered.
//    * @param  reached      Reached ops.
//    * @return Set of input ops to `destinations` (recursively) that have been reached.
//    */
//  private[this] def gatherInputs(destinations: Set[Op], reached: mutable.Set[Op]): Set[Op] = {
//    val inputs = mutable.Set.empty[Op]
//    val queue = mutable.Queue[Op](destinations.toSeq: _*)
//    while (queue.nonEmpty) {
//      val op = queue.dequeue()
//      if (reached.contains(op)) {
//        inputs += op
//        reached -= op // Done so we don't go through the same ops twice
//        op.inputs.foreach(i => queue.enqueue(i.op))
//      }
//    }
//    inputs.toSet
//  }
//
//  /** Initializes the back-propagation input counts for ops between two sets of ops.
//    *
//    * 'outputMap(op)' indicates the number of back-propagation inputs to this op.
//    *
//    * @param  sources                  Set of source ops.
//    * @param  destinations             Set of destination ops.
//    * @param  colocateGradientsWithOps Boolean value indicating whether to colocate the gradient ops with the original
//    *                                  ops.
//    * @return Tuple containing: (1) Map from op to the number of back-propagation inputs to this op, and (2) a control
//    *         flow state object which is not `null` if the ops between `sources` and `destinations` contain control flow
//    *         loops.
//    */
//  private[this] def countBackPropagationInputs(
//      sources: Set[Op], destinations: Set[Op], colocateGradientsWithOps: Boolean): (Map[Op, Int], Any) = {
//    // TODO: [CONTROL_FLOW]
//    // Mark ops reached when going from 'sources' to 'destinations'
//    val reached = mutable.Set[Op](destinations.toSeq: _*)
//    val reachedQueue = mutable.Queue[Op](sources.toSeq: _*)
//    while (reachedQueue.nonEmpty) {
//      val op = reachedQueue.dequeue()
//      if (!reached.contains(op)) {
//        reached += op
//        op.outputs.foreach(o => reachedQueue.enqueue(o.consumers.map(_.op): _*))
//      }
//    }
//
//    // Mark ops between 'sources' and 'destinations'
//    val between = mutable.Set.empty[Op]
//    val betweenList = mutable.ListBuffer.empty[Op] // TODO: [CONTROL_FLOW] Do we need the list aside from the set?
//    val betweenQueue = mutable.Queue[Op](destinations.toSeq: _*)
//    while (betweenQueue.nonEmpty) {
//      val op = betweenQueue.dequeue()
//      if (reached.contains(op)) {
//        between += op
//        betweenList += op
//        reached -= op // Done so we don't go through the same ops twice
//        op.inputs.foreach(i => betweenQueue.enqueue(i.op))
//      }
//    }
//
//    val loopState = null // TODO: [CONTROL_FLOW] !!!
//
//    // Initialize the pending counts for the between ops
//    val pendingCounts = mutable.Map.empty[Op, Int]
//    betweenList.flatMap(_.inputs).map(_.op).filter(between.contains).foreach(input => {
//      pendingCounts.update(input, pendingCounts.getOrElse(input, 0) + 1)
//    })
//
//    (pendingCounts.toMap, loopState)
//  }
//
//  /** Returns the set of ops that terminate the gradient computation.
//    *
//    * This function computes the frontier of the forward graph *before* which back-propagation should stop. Operations
//    * in the returned set will not be differentiated. This set is defined as the subset of `sources` containing ops that
//    * have no predecessor in `sources`. `pendingCounts` is the result of
//    * `countBackPropagationInputs(sources, destinations)`. An op has predecessors in `sources` if and only if
//    * `pendingCounts(op) > 0`.
//    *
//    * @param  sources       Source ops.
//    * @param  pendingCounts Result of `countBackPropagationInputs(sources, destinations)`.
//    * @return Set of ops that terminate the gradient computation.
//    */
//  private[this] def stopOps(sources: Set[Op], pendingCounts: Map[Op, Int]): Set[Op] = {
//    sources.filter(op => op.inputs.forall(i => pendingCounts(i.op) <= 0))
//  }
//
//  /** If `colocateGradientsWithOps` is `true`, then all ops created within `block` will be colocated with `op`.
//    *
//    * @param  op                       Op to maybe colocate with.
//    * @param  colocateGradientsWithOps Boolean value indicating whether to colocate the gradient ops with the original
//    *                                  ops.
//    * @param  block                    Block of code to execute using the specified colocation ops.
//    * @return Return value of `block`.
//    */
//  private[this] def maybeColocateWith[R](op: Op, colocateGradientsWithOps: Boolean)(block: => R): R = {
//    if (colocateGradientsWithOps)
//      Op.createWith(colocationOps = Set[Op](op))(block)
//    else
//      block
//  }
//
//  /** Fills in default values for the provided gradients, and checks whether their data types are correct.
//    *
//    * @param  dys                      Sequence containing tensor gradients.
//    * @param  ys                       Sequence containing the variables corresponding to `dys`.
//    * @param  colocateGradientsWithOps Boolean value indicating whether to colocate the gradient ops with the original
//    *                                  ops.
//    * @return Sequence containing the gradients tensors filled with the default value of `1`.
//    * @throws InvalidDataTypeException If the gradient tensor data types are not compatible with the input data types.
//    */
//  @throws[InvalidDataTypeException]
//  private[this] def setDefaultGradients(
//      dys: mutable.Seq[Op.OutputLike], ys: Seq[Op.OutputLike], colocateGradientsWithOps: Boolean): Unit = {
//    for (((y, dy), index) <- ys.zip(dys).zipWithIndex) {
//      if (dy eq null) {
//        if (y.dataType.isComplex)
//          throw InvalidDataTypeException(
//            s"Gradients of complex tensors must set 'gradients' (variable.dataType = '${y.dataType}').")
//        maybeColocateWith(y.op, colocateGradientsWithOps) {
//          y match {
//            case o: Op.Output => dys(index) = ArrayOps.onesLike(o)
//            case o: Op.OutputIndexedSlices =>
//              if (o.denseShape eq null)
//                throw new IllegalArgumentException(
//                  "The dense shape of output indexed slices must be known in order to obtain their gradients.")
//              val values = ArrayOps.fill(o.denseShape, 1.0)
//              dys(index) = Op.OutputIndexedSlices(indices = o.indices, values = values, denseShape = o.denseShape)
//            case o: Op.SparseOutput =>
//              val values = ArrayOps.fill(o.denseShape, 1.0)
//              dys(index) = Op.SparseOutput(indices = o.indices, values = values, denseShape = o.denseShape)
//          }
//        }
//      } else if ((y.dataType.isFloatingPoint || y.dataType.isInteger) &&
//          !dy.dataType.isFloatingPoint &&
//          !dy.dataType.isInteger) {
//        throw InvalidDataTypeException(
//          s"Gradient data type '${dy.dataType}' generated for real or integer-valued tensor '$y' with data type " +
//              s"'${y.dataType}' must be real or integer.")
//      } else if (y.dataType.isComplex && !dy.dataType.isComplex) {
//        throw InvalidDataTypeException(
//          s"Gradient data type '${dy.dataType}' generated for complex-valued tensor '$y' with data type " +
//              s"'${y.dataType}' must be complex.")
//      } else {
//        throw InvalidDataTypeException(
//          s"Tensor '$y' with data type '${y.dataType}' must be numeric in order to obtain a default gradient.")
//      }
//    }
//  }
//
//  /** Verifies that the provided `gradients` are valid in number and data type.
//    *
//    * @param  op        Op for which the gradients are being generated.
//    * @param  gradients Sequence containing the generated gradients.
//    * @throws IllegalStateException    If the generated gradients are not valid in number.
//    * @throws InvalidDataTypeException If the generated gradients are not valid in data type.
//    */
//  @throws[IllegalStateException]
//  @throws[InvalidDataTypeException]
//  private[this] def verifyGradients(op: Op, gradients: Seq[Op.OutputLike]): Unit = {
//    if (op.inputs.length != gradients.length)
//      throw new IllegalStateException(
//        s"The number of gradients (${gradients.length}) generated for op '$op' do not match its number of inputs " +
//            s"(${op.inputs.length}).")
//    for ((input, gradient) <- op.inputs.zip(gradients)) {
//      if (gradient ne null) {
//        if (gradient.dataType.isFloatingPoint && !input.dataType.isFloatingPoint)
//          throw InvalidDataTypeException(
//            s"Gradient data type '${gradient.dataType}' generated for real-valued op '$op' with data type " +
//                s"'${input.dataType}' must be real.")
//        else if (gradient.dataType.isComplex && !input.dataType.isComplex)
//          throw InvalidDataTypeException(
//            s"Gradient data type '${gradient.dataType}' generated for complex-valued op '$op' with data type " +
//                s"'${input.dataType}' must be complex.")
//        else
//          throw InvalidDataTypeException(
//            s"Gradient data type '${gradient.dataType}' generated for op '$op' with data type '${input.dataType}' " +
//                s"must be either real or complex.")
//      }
//    }
//  }
//
//  // TODO: [FUNCTIONAL] Symbolic gradient ('_SymGrad').
//
//  /** If the op was marked as compiled, this function compiles the calculation in `gradientFunction` (using XLA) and
//    * returns the result of `gradientFunction`. Otherwise, it simply returns the result of `gradientFunction`.
//    *
//    * @param  nameScope        Name scope to use for the gradient ops.
//    * @param  op               Op whose gradients are being computed.
//    * @param  gradientFunction Function that computes the gradients for `op`.
//    * @return Created gradients op.
//    */
//  private[this] def maybeCompile(nameScope: String, op: Op, gradientFunction: () => Op.Output): Op.Output = {
//    // TODO: [FUNCTIONAL] Add extra 'func' argument.
//    val cleanNameScope = nameScope.stripSuffix("/").replace('/', '_')
//    try {
//      val xlaCompile = op.booleanAttribute("_XlaCompile")
//      if (!xlaCompile) {
//        gradientFunction() // Exit early
//      } else {
//        val xlaSeparateCompileGradient = op.booleanAttribute("_XlaSeparateCompiledGradients")
//        val xlaScope = op.stringAttribute("_XlaScope")
//        // If the gradients are supposed to be compiled separately, we give them an '_XlaScope' name that is based on
//        // the name_scope of the gradients. Otherwise, they just inherit the existing '_XlaScope' name, which lets them
//        // be merged together with the non-gradient computation.
//        val xlaGradientsScope = if (xlaSeparateCompileGradient) s"${xlaScope}_grad_$cleanNameScope" else xlaScope
//        Op.createWith(attributes = Map("_XlaCompile" -> xlaCompile, "_XlaScope" -> xlaGradientsScope)) {
//          gradientFunction()
//        }
//      }
//    } catch {
//      case _: IllegalArgumentException =>
//        gradientFunction() // Something went wrong and so we exit
//    }
//  }
//
//  // TODO: Convert variable to op output by using its handle.
//  private[this] def getGradients(
//      ys: Seq[Op.OutputLike], xs: Seq[Op.OutputLike], initialGradients: Seq[Op.OutputLike] = null,
//      colocateGradientsWithOps: Boolean = false, gateGradients: Boolean = false,
//      name: String = "Gradients"): Seq[Op.Output] = {
//    val (ops, dys) = {
//      if (initialGradients != null) {
//        val ops = ys.map(_.op).toSet ++ xs.map(_.op).toSet ++ initialGradients.map(_.op).toSet
//        val dys = mutable.Seq[Op.OutputLike](initialGradients: _*)
//        (ops, dys)
//      } else {
//        val ops = ys.map(_.op).toSet ++ xs.map(_.op).toSet
//        val dys = mutable.Seq.fill[Op.OutputLike](ys.length)(null)
//        (ops, dys)
//      }
//    }
//    Op.createWithNameScope(name, ops) {
//      setDefaultGradients(dys, ys, colocateGradientsWithOps)
//      // The approach we take here is as follows: Create a list of all ops in the sub-graph between the ys and xs. Visit
//      // these ops in reverse order of ids to ensure that when we visit an op the gradients with respect to its outputs
//      // have been collected. Then, aggregate these gradients if needed, call the op's gradient function, and add the
//      // generated gradients to the gradients for its input.
//
//      // Initialize the pending count for ops in the connected sub-graph between the ys and xs.
//      if (ys.length > 1)
//        ys = ys.map(y => if (y.consumers()) ArrayOps.identity(y) else y)
//
//      if len(ys) > 1:
//          ys = [array_ops.identity(y) if y.consumers() else y for y in ys]
//      to_ops = [t.op for t in ys]
//      from_ops = [t.op for t in xs]
//      pending_count, loop_state = _PendingCount(ops.get_default_graph(), to_ops,
//        from_ops,
//        colocate_gradients_with_ops)
//    }
//  }
//}
