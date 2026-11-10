package app.cutekind.data

data class Question(val id: Int, val text: String, val spicy: Boolean)

object Questions {
    private val cute = listOf(
        "What is one small thing I do that always makes you smile?",
        "If I could teleport to you right now, what would we do first?",
        "What song reminds you of us?",
        "What was your first impression of me?",
        "Which memory of us do you replay the most?",
        "What would our perfect lazy Sunday together look like?",
        "What is something you want us to try together this year?",
        "What do you miss most about me today?",
        "If we adopted a pet tomorrow, what would it be and what would we name it?",
        "What meal would you cook for me tonight if I were there?",
        "When did you first know you liked me?",
        "What is a tiny habit of mine you secretly love?",
        "Where should our next trip together be?",
        "What made you think of me today?",
        "What is one thing I taught you without knowing it?",
        "If today had a title as a chapter of our story, what would it be?",
        "What is your favourite photo of us, and why?",
        "What would you put in a care package for me right now?",
        "What is something you are proud of me for?",
        "What movie should we watch together on our next call?",
        "What does home with me look like in five years?",
        "What is the best gift I have ever given you?",
        "What is one thing you want to hear from me more often?",
        "Which emoji is most me?",
        "What was the moment you felt closest to me, even from far away?",
        "What is something silly you want us to do when we are old?",
        "What part of your day do you most wish I had seen?",
        "If we had a whole day with no phones, how would we spend it?",
        "What is your favourite thing about how we talk?",
        "What tradition should we start as a couple?",
        "What is one fear I help you feel braver about?",
        "What would you name the playlist of this month with me?",
        "What do you want me to draw for you tomorrow?",
        "What is the first thing you will do when you see me next?",
        "What is one thing about your day you have not told me yet?",
        "What is a dream of yours I should know more about?",
        "What do I do that makes you feel most loved?",
        "What is one little thing I could do this week to make the distance easier?",
        "If we wrote a book about us, what would the first line be?",
        "What is your favourite way I say good morning or good night?",
        "What food do you want us to eat together next time?",
        "What is a question you have always wanted to ask me?",
        "What do you think we are really good at as a team?",
        "What scent, place or sound instantly reminds you of me?",
        "How did I surprise you the most since we met?",
        "What are three words for how you feel about us right now?",
        "What would our couple costume be?",
        "What is one thing you are looking forward to this week?",
        "What is the kindest thing I have done for you?",
        "If you could send me one hug with a note attached, what would the note say?",
    )

    private val spicy = listOf(
        "What outfit of mine can you not stop thinking about?",
        "Where is your favourite place to be kissed?",
        "What is the first thing you noticed about me that you found attractive?",
        "What would our ideal date night end like?",
        "What is one thing I do that drives you a little wild?",
        "What is a compliment you have been too shy to give me?",
        "What do you daydream about when you miss me?",
        "What is the most attractive thing I do without realising?",
        "Describe our next reunion kiss in three words.",
        "What text from me would make you blush instantly?",
        "What is a little fantasy date you have never told me about?",
        "What should I wear the next time we video call?",
        "Which of my photos is your secret favourite?",
        "What is the flirtiest thing you would whisper to me right now?",
        "What was the moment you most wanted to kiss me and could not?",
        "Slow dance in the kitchen or a long kiss in the rain?",
        "What is one thing you want to do the minute we are alone again?",
        "What nickname of mine makes your heart race?",
        "What is the boldest thing you would dare me to do on our next call?",
        "What do you find irresistible about my voice?",
    )

    /** Same question for both partners on the same UTC day. Every third day is spicy if enabled. */
    fun forDay(epochDay: Long, spicyOn: Boolean): Question =
        if (spicyOn && Math.floorMod(epochDay, 3L) == 0L) {
            val i = Math.floorMod(epochDay / 3, spicy.size.toLong()).toInt()
            Question(1000 + i, spicy[i], true)
        } else {
            val i = Math.floorMod(epochDay, cute.size.toLong()).toInt()
            Question(i, cute[i], false)
        }

    fun byId(id: Int): Question? =
        if (id >= 1000) spicy.getOrNull(id - 1000)?.let { Question(id, it, true) }
        else cute.getOrNull(id)?.let { Question(id, it, false) }
}
