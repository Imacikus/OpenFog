package com.openfog.online.achievements

import com.openfog.online.model.Achievement
import com.openfog.online.model.AchievementCondition

/** The 8 achievements (identical to the legacy web app). */
object DefaultAchievements {
    fun all(): List<Achievement> = listOf(
        Achievement("first_steps", "Erste Schritte", "Du hast deine ersten 1.000 Meter enthüllt!", "walk", AchievementCondition("distance", 1.0)),
        Achievement("explorer", "Entdecker", "Du hast 10 km² der Welt enthüllt!", "explore", AchievementCondition("area", 10.0)),
        Achievement("globetrotter", "Weltenbummler", "Du hast 0,0001% der Welt erkundet!", "globe", AchievementCondition("percent", 0.0001)),
        Achievement("marathon", "Marathon", "Du hast eine Marathon-Distanz (42,195 km) enthüllt!", "run", AchievementCondition("distance", 42.195)),
        Achievement("level_5", "Aufsteiger", "Du hast Level 5 erreicht!", "trending_up", AchievementCondition("level", 5.0)),
        Achievement("level_10", "Meister", "Du hast Level 10 erreicht!", "workspace_premium", AchievementCondition("level", 10.0)),
        Achievement("first_track", "Erster Track", "Du hast deinen ersten Track importiert!", "file_download", AchievementCondition("trackCount", 1.0)),
        Achievement("ten_tracks", "Sammler", "Du hast 10 Tracks importiert!", "folder", AchievementCondition("trackCount", 10.0))
    )
}
