package com.falakpatel.stridelocal.data

/** One built-in dish with nutrition for one typical serving. */
data class Dish(
    val name: String,
    val serving: String,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
)

/**
 * Starter list of common Indian (mostly Gujarati and everyday North/South Indian) foods.
 * Values are typical home-style estimates per serving, rounded. Real values vary with oil,
 * ghee and portion size, so the app lets you change servings or add your own food.
 * Next step: fill this from the Indian Nutrient Databank and link it to photo recognition.
 */
object IndianDishes {
    private fun d(n: String, s: String, k: Int, p: Double, c: Double, f: Double) = Dish(n, s, k.toDouble(), p, c, f)

    val all: List<Dish> = listOf(
        // Breads
        d("Roti / chapati", "1 medium", 100, 3.0, 18.0, 2.0),
        d("Phulka (no ghee)", "1 small", 70, 2.5, 14.0, 0.5),
        d("Plain paratha", "1", 200, 4.0, 28.0, 8.0),
        d("Aloo paratha", "1", 290, 6.0, 40.0, 11.0),
        d("Thepla", "1", 120, 3.0, 16.0, 5.0),
        d("Bajra / jowar rotla", "1", 120, 3.0, 24.0, 1.5),
        d("Puri", "1", 100, 2.0, 12.0, 5.0),
        d("Khakhra", "2", 100, 3.0, 16.0, 3.0),
        // Rice
        d("Plain rice", "1 cup cooked", 195, 4.0, 43.0, 0.5),
        d("Jeera rice", "1 cup", 240, 4.0, 44.0, 5.0),
        d("Khichdi", "1 cup", 220, 8.0, 36.0, 5.0),
        d("Veg pulao", "1 cup", 260, 5.0, 42.0, 8.0),
        d("Veg biryani", "1 cup", 300, 6.0, 45.0, 10.0),
        // Dal, kadhi, curries
        d("Toor dal", "1 katori", 150, 8.0, 20.0, 4.0),
        d("Gujarati dal", "1 katori", 140, 6.0, 20.0, 4.0),
        d("Gujarati kadhi", "1 katori", 120, 4.0, 10.0, 7.0),
        d("Rajma", "1 katori", 210, 10.0, 28.0, 6.0),
        d("Chole", "1 katori", 240, 10.0, 32.0, 8.0),
        d("Sambar", "1 katori", 130, 6.0, 18.0, 4.0),
        d("Paneer butter masala", "1 katori", 350, 13.0, 12.0, 28.0),
        d("Palak paneer", "1 katori", 260, 12.0, 10.0, 19.0),
        // Sabzi
        d("Mixed veg sabzi", "1 katori", 130, 3.0, 14.0, 7.0),
        d("Aloo sabzi", "1 katori", 170, 3.0, 24.0, 7.0),
        d("Bhindi sabzi", "1 katori", 120, 3.0, 10.0, 8.0),
        d("Sev tameta", "1 katori", 200, 4.0, 18.0, 12.0),
        d("Undhiyu", "1 katori", 250, 6.0, 24.0, 14.0),
        // Farsan and breakfast
        d("Dhokla", "4 pieces", 160, 6.0, 24.0, 4.0),
        d("Khandvi", "6 pieces", 150, 6.0, 14.0, 8.0),
        d("Handvo", "1 piece", 180, 6.0, 26.0, 6.0),
        d("Poha", "1 plate", 250, 5.0, 44.0, 6.0),
        d("Upma", "1 plate", 250, 6.0, 38.0, 8.0),
        d("Idli", "2", 120, 4.0, 24.0, 0.5),
        d("Plain dosa", "1", 170, 4.0, 28.0, 5.0),
        d("Masala dosa", "1", 330, 7.0, 48.0, 12.0),
        d("Uttapam", "1", 200, 5.0, 32.0, 6.0),
        // Street food and sweets
        d("Pav bhaji", "2 pav + bhaji", 450, 10.0, 60.0, 18.0),
        d("Vada pav", "1", 290, 6.0, 38.0, 13.0),
        d("Samosa", "1", 260, 4.0, 30.0, 14.0),
        d("Pani puri", "6 pieces", 200, 4.0, 30.0, 7.0),
        d("Gulab jamun", "1", 150, 2.0, 20.0, 7.0),
        d("Shrikhand", "1 katori", 250, 7.0, 35.0, 9.0),
        // Dairy and drinks
        d("Dahi (curd)", "1 katori", 60, 3.5, 4.5, 3.3),
        d("Chaas (buttermilk)", "1 glass", 40, 2.0, 3.0, 1.5),
        d("Milk (toned)", "1 glass", 120, 6.0, 10.0, 6.0),
        d("Chai with milk and sugar", "1 cup", 80, 2.0, 11.0, 3.0),
        d("Paneer", "100 g", 265, 18.0, 3.0, 20.0),
        // Fruit and protein
        d("Banana", "1", 105, 1.3, 27.0, 0.4),
        d("Apple", "1", 95, 0.5, 25.0, 0.3),
        d("Boiled egg", "1", 78, 6.0, 0.6, 5.0),
        d("Roasted chana", "30 g", 110, 6.0, 18.0, 2.0),
    )

    /** Case-insensitive match on any word start, so "dal" finds "Toor dal" and "Gujarati dal". */
    fun search(query: String): List<Dish> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return all
        return all.filter { d -> d.name.lowercase().let { it.startsWith(q) || it.contains(" $q") || it.contains("($q") || it.contains("/ $q") } }
    }
}
