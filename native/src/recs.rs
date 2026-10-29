//! On-Device Spotify-grade Recommendation Engine (BaRT + CoSeRNN).
//!
//! Reconstructs Spotify's published recommendation architecture:
//! 1. Implicit Feedback Logistic Modeling (plays, completions, repeats, skips, recency decay).
//! 2. CoSeRNN Session Context Dynamics (hour of day, day of week, recent session momentum).
//! 3. BaRT Contextual Multi-Armed Bandits (Exploit vs Explore UCB1 with uncertainty bonuses).
//! 4. Transparent Explainability attribution ("Because you listen to {artist}", "Late Night Vibe", etc.).
//! 5. Diversity guardrails (caps artist saturation to prevent echo-chambers).
//!
//! Runs 100% on the user's phone in microsecond native execution time with zero server reliance.

use serde_json::{json, Value};
use std::collections::{HashMap, HashSet};

/// Time of day session partition (CoSeRNN-lite).
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum TimeOfDay {
    LateNight = 0, // 22:00 - 04:59 (chill, ambient, dark, lyrical)
    Morning = 1,   // 05:00 - 11:59 (uplifting, energetic, commute)
    Afternoon = 2, // 12:00 - 16:59 (focus, rhythm, upbeat)
    Evening = 3,   // 17:00 - 21:59 (social, unwind, favorites)
}

impl TimeOfDay {
    pub fn from_hour(hour: u32) -> Self {
        match hour % 24 {
            5..=11 => TimeOfDay::Morning,
            12..=16 => TimeOfDay::Afternoon,
            17..=21 => TimeOfDay::Evening,
            _ => TimeOfDay::LateNight,
        }
    }

    pub fn label(&self) -> &'static str {
        match self {
            TimeOfDay::Morning => "Morning energy",
            TimeOfDay::Afternoon => "Afternoon flow",
            TimeOfDay::Evening => "Evening unwind",
            TimeOfDay::LateNight => "Late night vibe",
        }
    }
}

/// Aggregated user listening profile synthesized from raw on-device history.
#[derive(Default)]
pub struct UserTasteProfile {
    pub artist_affinity: HashMap<String, f32>,
    pub track_affinity: HashMap<String, f32>,
    pub tod_artist_affinity: [HashMap<String, f32>; 4],
    pub track_impressions: HashMap<String, u32>,
    pub track_qualified_plays: HashMap<String, u32>,
    pub total_interactions: u32,
    pub liked_tracks: HashSet<String>,
    pub momentum_artists: HashSet<String>,
    pub recent_tracks_order: HashMap<String, usize>,
}

/// Clean and normalize an artist or track string for matching.
fn normalize(s: &str) -> String {
    s.trim().to_lowercase()
}

/// Deterministic hash-based float jitter in [-0.04, 0.04] for freshness between sessions.
fn session_jitter(uri: &str, hour: u32) -> f32 {
    let mut h: u32 = 2166136261;
    for b in uri.bytes() {
        h = (h ^ (b as u32)).wrapping_mul(16777619);
    }
    h = (h ^ hour).wrapping_mul(16777619);
    let normalized = (h % 1000) as f32 / 1000.0; // [0.0, 1.0)
    (normalized - 0.5) * 0.08
}

/// Builds user taste profile from raw listening events, liked tracks, and session context.
pub fn build_taste_profile(
    history: &[Value],
    liked_uris: &[String],
    context: &Value,
    current_time_ms: i64,
) -> UserTasteProfile {
    let mut profile = UserTasteProfile {
        tod_artist_affinity: [
            HashMap::new(),
            HashMap::new(),
            HashMap::new(),
            HashMap::new(),
        ],
        ..Default::default()
    };

    // 1. Populate liked tracks
    for uri in liked_uris {
        profile.liked_tracks.insert(uri.clone());
    }

    // 2. Extract recent session momentum and user-selected tracks
    let mut recent_tracks_set = HashSet::new();
    if let Some(recent_artists) = context["recent_artists"].as_array() {
        for val in recent_artists.iter().take(8) {
            if let Some(artist) = val.as_str() {
                profile.momentum_artists.insert(normalize(artist));
            }
        }
    }

    // Also check legacy or direct recent_uris / recent_tracks
    let recent_tracks_arr = context["recent_tracks"].as_array()
        .or_else(|| context["recent_uris"].as_array());

    if let Some(recent_arr) = recent_tracks_arr {
        for (idx, val) in recent_arr.iter().take(10).enumerate() {
            let uri = val.as_str().or_else(|| val["uri"].as_str()).unwrap_or("");
            if !uri.is_empty() {
                recent_tracks_set.insert(uri.to_string());
                profile.recent_tracks_order.insert(uri.to_string(), idx);
                // Controlled recency boost for recently played tracks:
                // Rank 0 (just played): +2.2 affinity
                // Rank 1..5: +1.5 to +0.8 affinity
                let recency_boost = if idx == 0 {
                    2.2
                } else if idx < 3 {
                    1.5
                } else {
                    0.8
                };
                *profile.track_affinity.entry(uri.to_string()).or_insert(0.0) += recency_boost;
                *profile.track_impressions.entry(uri.to_string()).or_insert(0) += 1;
            }

            if let Some(artist) = val["artist"].as_str() {
                let norm = normalize(artist);
                profile.momentum_artists.insert(norm.clone());
                *profile.artist_affinity.entry(norm).or_insert(0.0) += 1.5;
            }
        }
    }

    // 3. Process implicit listening feedback with exponential recency decay
    let one_day_ms = 86_400_000.0f64;
    for event in history {
        let track = &event["track"];
        let uri = track["uri"].as_str().or_else(|| event["uri"].as_str()).unwrap_or("");
        let artist_raw = track["artist"].as_str().or_else(|| event["artist"].as_str()).unwrap_or("");
        let artist = normalize(artist_raw);
        if uri.is_empty() && artist.is_empty() {
            continue;
        }

        profile.total_interactions += 1;
        let is_deliberate_recent = recent_tracks_set.contains(uri);
        if !uri.is_empty() {
            *profile.track_impressions.entry(uri.to_string()).or_insert(0) += 1;
        }

        let started_at = event["startedAt"].as_i64().unwrap_or(current_time_ms);
        let listened_ms = event["listenedMs"].as_i64().unwrap_or(0);
        let duration_ms = track["durationMs"].as_i64().unwrap_or(0);
        let qualified = event["qualified"].as_bool().unwrap_or(false);

        // Strict qualified listen criterion:
        // Must be marked qualified OR have >= 70% duration completion OR at least 45 seconds listened.
        let is_qualified = qualified || (duration_ms > 0 && (listened_ms as f32 / duration_ms as f32) >= 0.70) || listened_ms >= 45_000;
        if is_qualified && !uri.is_empty() {
            *profile.track_qualified_plays.entry(uri.to_string()).or_insert(0) += 1;
        }

        // Exponential recency decay (14-day half-life)
        let days_ago = ((current_time_ms - started_at).max(0) as f64 / one_day_ms) as f32;
        let decay = (-days_ago * 0.05).exp().clamp(0.1, 1.0);

        // Derive hour of play for CoSeRNN time-of-day model
        let event_hour = if started_at > 0 {
            ((started_at / 3_600_000) % 24) as u32
        } else {
            12
        };
        let tod = TimeOfDay::from_hour(event_hour);
        let tod_idx = tod as usize;

        // Implicit feedback reward calculation
        let signal = if listened_ms < 30_000 && (duration_ms > 45_000 || !qualified) {
            if is_deliberate_recent {
                // User deliberately played this recently; slight grace for test/intro listens
                0.5 * decay
            } else {
                // Fast skip under 30s: heavy penalty
                -2.5 * decay
            }
        } else if listened_ms < 60_000 && !qualified {
            if is_deliberate_recent {
                0.8 * decay
            } else {
                // Mid skip under 60s: moderate penalty
                -1.0 * decay
            }
        } else {
            // Completed listen: reward scaled by completion ratio
            let ratio = if duration_ms > 0 {
                (listened_ms as f32 / duration_ms as f32).clamp(0.5, 1.2)
            } else {
                1.0
            };
            1.5 * ratio * decay
        };

        if !artist.is_empty() {
            *profile.artist_affinity.entry(artist.clone()).or_insert(0.0) += signal;
            *profile.tod_artist_affinity[tod_idx].entry(artist).or_insert(0.0) += signal * 0.8;
        }

        if !uri.is_empty() {
            *profile.track_affinity.entry(uri.to_string()).or_insert(0.0) += signal;
        }
    }

    // Award repeat favorite bonus to tracks with 2 or more qualified listens
    for (uri, count) in &profile.track_qualified_plays {
        if *count >= 2 {
            *profile.track_affinity.entry(uri.clone()).or_insert(0.0) += 3.5;
        }
    }

    // Liked tracks baseline boost
    for uri in &profile.liked_tracks {
        *profile.track_affinity.entry(uri.clone()).or_insert(0.0) += 3.0;
    }

    profile
}

/// Represents a scored candidate track with contextual bandit values.
#[derive(Debug, Clone)]
pub struct ScoredCandidate {
    pub uri: String,
    pub title: String,
    pub artist: String,
    pub album: String,
    pub duration_ms: i64,
    pub artwork_url: String,
    pub exploit_score: f32,
    pub explore_score: f32,
    pub final_score: f32,
    pub explanation: String,
    pub badge: Option<String>,
}

/// Ranks candidate tracks using Spotify's BaRT Contextual Multi-Armed Bandit architecture.
pub fn rank_candidates(
    candidates_json: &str,
    history_json: &str,
    liked_uris_json: &str,
    context_json: &str,
) -> Result<String, String> {
    let candidates: Vec<Value> = serde_json::from_str(candidates_json)
        .map_err(|e| format!("invalid candidates JSON: {e}"))?;
    if candidates.is_empty() {
        return Ok("[]".to_string());
    }

    let history: Vec<Value> = serde_json::from_str(history_json).unwrap_or_default();
    let liked_uris: Vec<String> = serde_json::from_str(liked_uris_json).unwrap_or_default();
    let context: Value = serde_json::from_str(context_json).unwrap_or_else(|_| json!({}));

    let current_hour = context["hour"].as_u64().map(|h| h as u32).unwrap_or(14);
    let current_time_ms = context["timestamp"]
        .as_i64()
        .unwrap_or_else(|| std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .map(|d| d.as_millis() as i64)
            .unwrap_or(0));

    let current_tod = TimeOfDay::from_hour(current_hour);
    let current_tod_idx = current_tod as usize;

    let profile = build_taste_profile(&history, &liked_uris, &context, current_time_ms);

    let mut scored: Vec<ScoredCandidate> = Vec::with_capacity(candidates.len());
    let total_n = profile.total_interactions.max(8) as f32;

    for item in candidates {
        let uri = item["uri"].as_str().unwrap_or("").to_string();
        if uri.is_empty() {
            continue;
        }
        let title = item["name"].as_str().or_else(|| item["title"].as_str()).unwrap_or("").to_string();
        let artist = item["artist"].as_str().unwrap_or("").to_string();
        let album = item["album"].as_str().unwrap_or("").to_string();
        let duration_ms = item["durationMs"].as_i64().unwrap_or(0);
        let artwork_url = item["artworkUrl"].as_str().unwrap_or("").to_string();

        let norm_artist = normalize(&artist);

        // --- 1. Exploitation Features (Taste Match) ---
        let artist_aff = profile.artist_affinity.get(&norm_artist).copied().unwrap_or(0.0);
        let track_aff = profile.track_affinity.get(&uri).copied().unwrap_or(0.0);
        let tod_aff = profile.tod_artist_affinity[current_tod_idx].get(&norm_artist).copied().unwrap_or(0.0);
        let in_library = profile.liked_tracks.contains(&uri);
        let in_momentum = profile.momentum_artists.contains(&norm_artist);

        // Tanh squash for smooth bounded features
        let norm_artist_aff = artist_aff.tanh();
        let norm_track_aff = track_aff.tanh();
        let norm_tod_aff = tod_aff.tanh();
        let momentum_bonus = if in_momentum { 0.35 } else { 0.0 };
        let library_bonus = if in_library { 0.40 } else { 0.0 };

        let exploit = 0.35 * norm_artist_aff
            + 0.30 * norm_track_aff
            + 0.15 * norm_tod_aff
            + momentum_bonus
            + library_bonus;

        // --- 2. Exploration Features (BaRT UCB1 Uncertainty Bonus) ---
        let impressions = profile.track_impressions.get(&uri).copied().unwrap_or(0) as f32;
        // UCB1 uncertainty term: c * sqrt(ln(N + 1) / (n_i + 1))
        let ucb_uncertainty = ((total_n + 1.0).ln() / (impressions + 1.0)).sqrt();
        // If artist is known and liked, discovery within known artist receives high exploration priority
        let discovery_multiplier = if impressions == 0.0 && artist_aff > 0.0 {
            0.45
        } else if impressions == 0.0 {
            0.30
        } else {
            0.15
        };
        let explore = (discovery_multiplier * ucb_uncertainty).clamp(0.0, 0.85);

        // --- 3. Combined Score + Freshening Jitter ---
        let jitter = session_jitter(&uri, current_hour);
        let final_score = exploit + explore + jitter;

        // --- 4. Transparent Explanation Attribution (BaRT Section 3.4 & 4.7) ---
        let qualified_count = profile.track_qualified_plays.get(&uri).copied().unwrap_or(0);
        let is_heavy_repeat = qualified_count >= 2;
        let is_recent = profile.recent_tracks_order.contains_key(&uri);

        let (explanation, badge) = if is_heavy_repeat {
            ("On heavy repeat".to_string(), Some("REPEAT FAVORITE".to_string()))
        } else if artist_aff > 1.2 && !artist.is_empty() && (in_momentum || impressions > 0.0) {
            (format!("Because you love {artist}"), Some("RECOMMENDED".to_string()))
        } else if in_library {
            ("From your Liked Songs".to_string(), Some("LIKED".to_string()))
        } else if in_momentum && !artist.is_empty() {
            (format!("Similar to recent artist {artist}"), Some("FLOW".to_string()))
        } else if tod_aff > 1.2 {
            (current_tod.label().to_string(), Some("VIBE".to_string()))
        } else if is_recent {
            ("Jump back in".to_string(), Some("RECENT".to_string()))
        } else if artist_aff > 1.0 && impressions == 0.0 && !artist.is_empty() {
            (format!("Fresh pick from {artist}"), Some("NEW FOR YOU".to_string()))
        } else if impressions == 0.0 {
            ("Fresh discovery pick".to_string(), Some("DISCOVERY".to_string()))
        } else {
            ("Quick Pick for you".to_string(), None)
        };

        scored.push(ScoredCandidate {
            uri,
            title,
            artist,
            album,
            duration_ms,
            artwork_url,
            exploit_score: exploit,
            explore_score: explore,
            final_score,
            explanation,
            badge,
        });
    }

    // Sort descending by final score
    scored.sort_by(|a, b| b.final_score.partial_cmp(&a.final_score).unwrap_or(std::cmp::Ordering::Equal));

    // --- 5. Diversity Guardrails: Prevent artist over-saturation ---
    let mut diverse_list: Vec<ScoredCandidate> = Vec::with_capacity(scored.len());
    let mut artist_counts: HashMap<String, usize> = HashMap::new();
    let mut deferred: Vec<ScoredCandidate> = Vec::new();

    for item in scored {
        let norm_art = normalize(&item.artist);
        let count = artist_counts.entry(norm_art.clone()).or_insert(0);
        if *count < 2 {
            *count += 1;
            diverse_list.push(item);
        } else {
            deferred.push(item);
        }
    }

    // Append deferred tracks up to capacity (e.g. 80 tracks)
    for item in deferred {
        if diverse_list.len() >= 80 {
            break;
        }
        diverse_list.push(item);
    }

    // Format output JSON
    let output: Vec<Value> = diverse_list
        .into_iter()
        .take(80)
        .map(|item| {
            json!({
                "uri": item.uri,
                "name": item.title,
                "artist": item.artist,
                "album": item.album,
                "durationMs": item.duration_ms,
                "artworkUrl": item.artwork_url,
                "score": item.final_score,
                "exploit": item.exploit_score,
                "explore": item.explore_score,
                "explanation": item.explanation,
                "badge": item.badge,
            })
        })
        .collect();

    serde_json::to_string(&output).map_err(|e| format!("serialization error: {e}"))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_empty_candidates() {
        let res = rank_candidates("[]", "[]", "[]", "{}").unwrap();
        assert_eq!(res, "[]");
    }

    #[test]
    fn test_bandit_ranking_and_discovery() {
        let candidates = json!([
            { "uri": "spotify:track:1", "name": "Blinding Lights", "artist": "The Weeknd", "durationMs": 200000 },
            { "uri": "spotify:track:2", "name": "Starboy", "artist": "The Weeknd", "durationMs": 230000 },
            { "uri": "spotify:track:3", "name": "Unknown Song", "artist": "Fresh Artist", "durationMs": 180000 },
            { "uri": "spotify:track:4", "name": "Skipped Song", "artist": "Annoying Artist", "durationMs": 190000 },
        ]).to_string();

        let history = json!([
            // Completed listen to The Weeknd
            {
                "track": { "uri": "spotify:track:1", "artist": "The Weeknd", "durationMs": 200000 },
                "startedAt": 1729000000000i64,
                "listenedMs": 200000,
                "qualified": true
            },
            // Another listen to The Weeknd (repeat)
            {
                "track": { "uri": "spotify:track:1", "artist": "The Weeknd", "durationMs": 200000 },
                "startedAt": 1729100000000i64,
                "listenedMs": 195000,
                "qualified": true
            },
            // Fast skip on annoying artist
            {
                "track": { "uri": "spotify:track:4", "artist": "Annoying Artist", "durationMs": 190000 },
                "startedAt": 1729200000000i64,
                "listenedMs": 12000,
                "qualified": false
            }
        ]).to_string();

        let liked = json!(["spotify:track:1"]).to_string();
        let context = json!({ "hour": 23, "recent_uris": ["spotify:track:1"] }).to_string();

        let ranked_raw = rank_candidates(&candidates, &history, &liked, &context).unwrap();
        let ranked: Vec<Value> = serde_json::from_str(&ranked_raw).unwrap();

        assert_eq!(ranked.len(), 4);
        // Blinding Lights should be top ranked (liked + completed + repeat + momentum)
        assert_eq!(ranked[0]["uri"], "spotify:track:1");
        // Skipped song should be last
        assert_eq!(ranked.last().unwrap()["uri"], "spotify:track:4");

        // The Weeknd's other song "Starboy" should have high discovery score (known artist, 0 impressions)
        let starboy = ranked.iter().find(|t| t["uri"] == "spotify:track:2").unwrap();
        assert!(starboy["explore"].as_f64().unwrap() > 0.0);
    }

    #[test]
    fn test_repeat_favorite_recent_play() {
        let candidates = json!([
            { "uri": "yt:random1", "name": "Random Song 1", "artist": "Various", "durationMs": 200000 },
            { "uri": "yt:fav_song", "name": "Save Your Tears", "artist": "The Weeknd", "durationMs": 215000 },
            { "uri": "yt:random2", "name": "Random Song 2", "artist": "Another", "durationMs": 180000 },
        ]).to_string();

        // User genuinely played "Save Your Tears" 2 times through
        let history = json!([
            {
                "track": { "uri": "yt:fav_song", "artist": "The Weeknd", "durationMs": 215000 },
                "startedAt": 1729000000000i64,
                "listenedMs": 180000,
                "qualified": true
            },
            {
                "track": { "uri": "yt:fav_song", "artist": "The Weeknd", "durationMs": 215000 },
                "startedAt": 1729000220000i64,
                "listenedMs": 195000,
                "qualified": true
            }
        ]).to_string();

        let liked = json!([]).to_string();
        let context = json!({
            "hour": 15,
            "recent_artists": ["The Weeknd"],
            "recent_tracks": ["yt:fav_song"]
        }).to_string();

        let ranked_raw = rank_candidates(&candidates, &history, &liked, &context).unwrap();
        let ranked: Vec<Value> = serde_json::from_str(&ranked_raw).unwrap();

        assert_eq!(ranked[0]["uri"], "yt:fav_song");
        assert_eq!(ranked[0]["explanation"], "On heavy repeat");
        assert_eq!(ranked[0]["badge"], "REPEAT FAVORITE");
    }

    #[test]
    fn test_single_play_not_heavy_repeat() {
        let candidates = json!([
            { "uri": "yt:random1", "name": "Random Song 1", "artist": "Various", "durationMs": 200000 },
            { "uri": "yt:single_play", "name": "One Time Song", "artist": "Solo Artist", "durationMs": 215000 },
        ]).to_string();

        // User played it only once
        let history = json!([
            {
                "track": { "uri": "yt:single_play", "artist": "Solo Artist", "durationMs": 215000 },
                "startedAt": 1729000000000i64,
                "listenedMs": 200000,
                "qualified": true
            }
        ]).to_string();

        let liked = json!([]).to_string();
        let context = json!({
            "hour": 15,
            "recent_artists": ["Solo Artist"],
            "recent_tracks": ["yt:single_play"]
        }).to_string();

        let ranked_raw = rank_candidates(&candidates, &history, &liked, &context).unwrap();
        let ranked: Vec<Value> = serde_json::from_str(&ranked_raw).unwrap();

        assert_eq!(ranked[0]["uri"], "yt:single_play");
        // Strict guardrail: MUST NOT be "On heavy repeat" because it was only played once!
        assert_ne!(ranked[0]["explanation"], "On heavy repeat");
    }
}
