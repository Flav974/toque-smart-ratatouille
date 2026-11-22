package app.ratatouille.core

import app.ratatouille.core.courses.Courses
import app.ratatouille.core.courses.QrCode
import app.toque.core.recipes.Ingredient
import app.toque.core.recipes.IngredientGroup
import app.toque.core.recipes.Recipe
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoursesTest {
    @Test fun capacitiesMatchTheStandard() {
        assertEquals(16, QrCode.dataCodewords(1, QrCode.Ecc.M))
        assertEquals(19, QrCode.dataCodewords(1, QrCode.Ecc.L))
        assertEquals(28, QrCode.dataCodewords(2, QrCode.Ecc.M))
        assertEquals(216, QrCode.dataCodewords(10, QrCode.Ecc.M))
        assertEquals(2956, QrCode.dataCodewords(40, QrCode.Ecc.L))
        assertEquals(3706, QrCode.rawDataModules(40) / 8)
        assertArrayEquals(intArrayOf(6, 18), QrCode.alignmentPositions(2))
        assertArrayEquals(intArrayOf(6, 22, 38), QrCode.alignmentPositions(7))
        assertArrayEquals(intArrayOf(6, 34, 60, 86, 112, 138), QrCode.alignmentPositions(32))
    }

    @Test fun encodesWithSmallestVersion() {
        val q = QrCode.encode("Bonjour")!!
        assertEquals(1, q.version)
        assertEquals(21, q.size)
        // Motifs de repérage : coins foncés, séparateur clair.
        assertTrue(q.get(0, 0) && q.get(6, 6) && !q.get(7, 7) && q.get(20, 0) && q.get(0, 20))
        // Module foncé obligatoire.
        assertTrue(q.get(8, q.size - 8))
        val long = QrCode.encode("é".repeat(400))!!
        assertTrue(long.version in 10..40)
        assertNull(QrCode.encode("x".repeat(4000)))
    }

    @Test fun shoppingListText() {
        val r = Recipe(
            id = "r", title = "Risotto", servings = 4,
            ingredients = listOf(IngredientGroup("", listOf(Ingredient("320", "g", "riz arborio"), Ingredient("1", "", "oignon"))),
                IngredientGroup("Pour servir", listOf(Ingredient("", "", "parmesan")))),
        )
        assertEquals("Courses : Risotto (4 pers.)\n- 320 g riz arborio\n- 1 oignon\n\nPour servir :\n- parmesan", Courses.texte(r))
        val enorme = r.copy(ingredients = listOf(IngredientGroup("", List(200) { Ingredient("1", "kg", "ingrédient numéro $it") })))
        val t = Courses.texte(enorme)
        assertTrue(t.toByteArray().size <= Courses.MAX_OCTETS && t.endsWith("…"))
        assertNotNull(QrCode.encode(t))
    }
}
