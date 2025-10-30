package security

import de.mkammerer.argon2.Argon2Factory

private val argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id)

// revisar como sera el hash despues 
fun hashPassword(plain: String): String =
    argon2.hash(2, 19_456, 1, plain.toCharArray())   // t=2, m≈19MB, p=1 (ajusta a tu server)

fun verifyPassword(plain: String, hash: String): Boolean =
    argon2.verify(hash, plain.toCharArray())
