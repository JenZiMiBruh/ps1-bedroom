package com.ps1bedroom.room.audio

/** Each game's title jingle (HTML game list `sting`): wave, notes, step between notes, an optional thump. */
class Sting(val wave: String, val notes: List<String>, val step: Double, val thump: String? = null)
object Stings {
    val BY_TITLE: Map<String, Sting> = mapOf(
        "Grand Theft Auto" to Sting("square", listOf("E3", "G3", "A3", "C4", "B3"), 0.12, "E1"),
        "Grand Theft Auto: London 1969" to Sting("sawtooth", listOf("G3", "B3", "D4", "G4"), 0.14, "G1"),
        "Grand Theft Auto 2" to Sting("square", listOf("A3", "C4", "E4", "A4", "G4"), 0.1, "A1"),
        "Grand Theft Auto III" to Sting("sawtooth", listOf("D3", "F3", "A3", "D4", "C4"), 0.13, "D1"),
        "Grand Theft Auto: Vice City" to Sting("square", listOf("C4", "E4", "G4", "B4", "C5"), 0.1, "C2"),
        "Grand Theft Auto: San Andreas" to Sting("sine", listOf("G3", "A#3", "D4", "F4", "G4"), 0.14, "G1"),
        "Grand Theft Auto: Liberty City Stories" to Sting("sawtooth", listOf("E3", "G3", "B3", "E4"), 0.13, "E1"),
        "Grand Theft Auto: Vice City Stories" to Sting("square", listOf("A3", "C#4", "E4", "A4", "E5"), 0.1, "A1"),
        "Grand Theft Auto IV" to Sting("sawtooth", listOf("C3", "D#3", "G3", "C4"), 0.18, "C1"),
        "Grand Theft Auto: Chinatown Wars" to Sting("triangle", listOf("D4", "E4", "G4", "A4", "D5"), 0.11, "D2"),
        "Grand Theft Auto V" to Sting("sawtooth", listOf("F3", "A3", "C4", "F4", "E4"), 0.13, "F1"),
        "Grand Theft Auto VI" to Sting("sawtooth", listOf("A3", "E4", "A4", "C#5", "E5"), 0.12, "A1"),
        "Tony Hawk's Pro Skater 2" to Sting("square", listOf("E4", "G4", "A4", "B4", "E5"), 0.08, "E2"),
        "Spyro: Year of the Dragon" to Sting("triangle", listOf("C5", "E5", "G5", "C6", "E6"), 0.07),
        "Final Fantasy IX" to Sting("sine", listOf("C4", "G4", "C5", "D5", "E5", "G5", "C6"), 0.11),
        "Spider-Man" to Sting("sawtooth", listOf("D4", "D4", "A4", "D5"), 0.14, "D2"),
        "Crash Bash" to Sting("square", listOf("G4", "C5", "E5", "G5", "C5", "G5"), 0.07),
        "Driver 2" to Sting("sawtooth", listOf("E3", "G3", "A3", "E4"), 0.12, "E1"),
        "Medal of Honor: Underground" to Sting("sawtooth", listOf("G3", "C4", "E4", "G4"), 0.2, "C2"),
        "Dino Crisis 2" to Sting("sawtooth", listOf("C3", "C#3", "G3", "F#3"), 0.18, "A0"),
        "Harry Potter and the Sorcerer's Stone" to Sting("sine", listOf("B4", "E5", "G5", "F#5", "E5", "B5"), 0.13),
        "Yu-Gi-Oh! Forbidden Memories" to Sting("square", listOf("A3", "C4", "E4", "A4", "E5"), 0.1, "A1"),
        "Tony Hawk's Pro Skater 3" to Sting("square", listOf("A4", "C5", "D5", "E5", "A5"), 0.08, "A1"),
        "Bandi-coot Rush" to Sting("triangle", listOf("G4", "B4", "D5", "G5"), 0.09),
    )
}
