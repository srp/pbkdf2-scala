/**
 *  Copyright 2012 Nicolas Rémond (@nremond)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.github.nremond

import java.nio.{ByteBuffer, IntBuffer}

import scala.Array.canBuildFrom

import javax.crypto

object PBKDF2 {

  /**
   * Implements PBKDF2 as defined in RFC 2898, section 5.2
   *
   * HMAC+SHA256 is used as the default pseudo random function.
   *
   * Right now 20,000 iterations is the strictly recommended default minimum. It takes 100ms on a i5 M-580 2.6GHz CPU.
   * The minimum increases every year, please that in mind.
   * You may want to use the ScalaMeter test to tune your settings.
   *
   * @password : the password to encrypt
   * @salt : the RFC 2898 recommends salt that is at least 64 bits long
   * @iterations : the number of encryption iterations
   * @dkLength : derived-key length
   * @cryptoAlgo : HMAC+SHA256 is the default as HMAC+SHA1 is now considered weak
   * @return the encrypted password
   */
  def apply(password: String, salt: String, iterations: Int = 20000, dkLength: Int = 32, cryptoAlgo: String = "HmacSHA256"): String = {

    val mac = crypto.Mac.getInstance(cryptoAlgo)
    val saltBuff = salt.getBytes("UTF8")
    mac.init(new crypto.spec.SecretKeySpec(password.getBytes("UTF8"), "RAW"))

    def bytesFromInt(i: Int) = ByteBuffer.allocate(4).putInt(i).array

    def xor(buff: IntBuffer, a2: Array[Byte]) {
      val b2 = ByteBuffer.wrap(a2).asIntBuffer
      buff.array.indices.foreach(i => buff.put(i, buff.get(i) ^ b2.get(i)))
    }

    // pseudo-random function defined in the spec
    def prf(buff: Array[Byte]) = mac.doFinal(buff)

    // this is a translation of the helper function "F" defined in the spec
    def calculateBlock(blockNum: Int): Array[Byte] = {
      // u_1
      val u_1 = prf(saltBuff ++ bytesFromInt(blockNum))

      val buff = IntBuffer.allocate(u_1.length / 4).put(ByteBuffer.wrap(u_1).asIntBuffer)
      var u = u_1
      var iter = 1
      while (iter < iterations) {
        // u_2 through u_c : calculate u_n and xor it with the previous value
        u = prf(u)
        xor(buff, u)
        iter += 1
      }

      val ret = ByteBuffer.allocate(u_1.length)
      buff.array.foreach { case i => ret.putInt(i) }
      ret.array
    }

    // how many blocks we'll need to calculate (the last may be truncated)
    val blocksNeeded = (dkLength.toFloat / 20).ceil.toInt

    (1 to blocksNeeded).map(calculateBlock(_).map("%02x" format _)).flatten.mkString.substring(0, dkLength * 2)
  }
}