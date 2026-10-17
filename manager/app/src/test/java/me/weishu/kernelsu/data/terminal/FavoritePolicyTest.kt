package me.weishu.kernelsu.data.terminal

import org.junit.Assert.*
import org.junit.Test

class FavoritePolicyTest {
    @Test fun preservesQuotesWhitespaceAndMultilineCommands() {
        val item = CommandFavorite(name = "Inspect", command = "  printf '%s\\n' '\$PATH'\nprintf '\u7b2c\u4e8c\u884c'  ", group = "Diagnostics", pinned = true)
        assertEquals(listOf(item), FavoritePolicy.decode(FavoritePolicy.encode(listOf(item))))
    }
    @Test fun rejectsAmbiguousIdentityAndResourceOverflow() {
        val item = CommandFavorite(name = "A", command = "id")
        assertThrows(IllegalArgumentException::class.java) { FavoritePolicy.validate(listOf(item, item)) }
        assertThrows(IllegalArgumentException::class.java) { FavoritePolicy.validate(List(5) { CommandFavorite(name = "A", command = "id", pinned = true) }) }
        assertThrows(IllegalArgumentException::class.java) { FavoritePolicy.validate(listOf(item.copy(command = "x".repeat(16385)))) }
        assertThrows(IllegalArgumentException::class.java) { FavoritePolicy.validate(listOf(item.copy(command = "id\u0000"))) }
        assertThrows(IllegalArgumentException::class.java) { FavoritePolicy.decode("{\"schema\":9}") }
    }
}
