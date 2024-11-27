import chisel3._
import chisel3.util.Decoupled

class Passthroughwithparas(Width: Int) extends Module {
  val io = IO(new Bundle {
    val in =  Input(UInt(Width.W))
    val out = Output(UInt(Width.W))
  })
  io.out := io.in
  // printf("Print during simulation: Input is %d\n", io.in)
  // println(s"Print during generation: Input is ${io.in}")
}

object Passthrough extends App {
  println(getVerilogString(new Passthroughwithparas(10)))
}