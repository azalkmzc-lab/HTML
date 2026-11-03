package com.example.data

object SampleTemplates {

    val ARABIC_SPORTS_NEWS_IPTV_HTML = """
<!DOCTYPE html>
<html lang="ar" dir="rtl">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>بث مباشر - القنوات الرياضية والإخبارية</title>
    <link rel="stylesheet" href="style.css">
</head>
<body>
    <header class="top-header">
        <div class="brand">
            <span class="live-dot"></span>
            <h1>MAX IPTV STUDIO LIVE</h1>
        </div>
        <div class="category-tabs">
            <button class="tab active" onclick="filterCategory('الكل')">الكل</button>
            <button class="tab" onclick="filterCategory('إخبارية')">إخبارية</button>
            <button class="tab" onclick="filterCategory('رياضية')">رياضية</button>
            <button class="tab" onclick="filterCategory('وثائقية')">وثائقية</button>
            <button class="tab" onclick="filterCategory('أفلام')">أفلام ومسلسلات</button>
        </div>
    </header>

    <main class="container">
        <!-- Old HTML5 Video Player (Will be intercepted & upgraded to Native Media3 ExoPlayer 1.5.1) -->
        <section class="player-wrapper">
            <video id="html5-video-player" controls poster="https://picsum.photos/seed/iptv/800/450">
                <source src="https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8" type="application/x-mpegURL">
            </video>
            <div class="now-playing-bar">
                <span id="current-channel-title">قناة الفضاء الوثائقية HD (بث HLS مباشر)</span>
                <span class="badge-native">Media3 ExoPlayer 1.5.1 Ready</span>
            </div>
        </section>

        <section class="channels-grid" id="channels-container">
            <!-- Channel 1: HLS M3U8 Live Stream -->
            <div class="channel-card" data-category="وثائقية" data-stream="https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8" data-backup="https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8" onclick="playChannel('قناة الوثائقية الفضائية HD', 'https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8', 'وثائقية')">
                <img src="https://picsum.photos/seed/doc1/120/120" alt="قناة الوثائقية الفضائية HD" class="channel-logo">
                <div class="channel-info">
                    <h3>قناة الوثائقية الفضائية HD</h3>
                    <span class="cat-tag">وثائقية • HLS M3U8</span>
                </div>
                <button class="play-btn">تشغيل البث</button>
            </div>

            <!-- Channel 2: Akamai Live HLS Test Stream -->
            <div class="channel-card" data-category="إخبارية" data-stream="https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8" data-backup="https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8" onclick="playChannel('الإخبارية العالمية المباشرة 24', 'https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8', 'إخبارية')">
                <img src="https://picsum.photos/seed/news24/120/120" alt="الإخبارية العالمية المباشرة 24" class="channel-logo">
                <div class="channel-info">
                    <h3>الإخبارية العالمية المباشرة 24</h3>
                    <span class="cat-tag">إخبارية • LIVE 24/7</span>
                </div>
                <button class="play-btn">تشغيل البث</button>
            </div>

            <!-- Channel 3: Apple BipBop Multi-bitrate HLS Stream -->
            <div class="channel-card" data-category="رياضية" data-stream="https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_fmp4/master.m3u8" data-backup="https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8" onclick="playChannel('الرياضية بريميوم 1 FHD', 'https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_fmp4/master.m3u8', 'رياضية')">
                <img src="https://picsum.photos/seed/sport1/120/120" alt="الرياضية بريميوم 1 FHD" class="channel-logo">
                <div class="channel-info">
                    <h3>الرياضية بريميوم 1 FHD</h3>
                    <span class="cat-tag">رياضية • Multi-Bitrate M3U8</span>
                </div>
                <button class="play-btn">تشغيل البث</button>
            </div>

            <!-- Channel 4: DASH MPD Stream -->
            <div class="channel-card" data-category="أفلام" data-stream="https://dash.akamaized.net/akamai/bbb_30fps/bbb_30fps.mpd" data-backup="https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4" onclick="playChannel('سينما ماكس DASH 4K', 'https://dash.akamaized.net/akamai/bbb_30fps/bbb_30fps.mpd', 'أفلام')">
                <img src="https://picsum.photos/seed/cinema4k/120/120" alt="سينما ماكس DASH 4K" class="channel-logo">
                <div class="channel-info">
                    <h3>سينما ماكس DASH 4K</h3>
                    <span class="cat-tag">أفلام • DASH MPD</span>
                </div>
                <button class="play-btn">تشغيل البث</button>
            </div>

            <!-- Channel 5: Direct MP4 HD Stream -->
            <div class="channel-card" data-category="أفلام" data-stream="https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4" data-backup="https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4" onclick="playChannel('أفلام الخيال العلمي Studio HD', 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4', 'أفلام')">
                <img src="https://picsum.photos/seed/scifi/120/120" alt="أفلام الخيال العلمي Studio HD" class="channel-logo">
                <div class="channel-info">
                    <h3>أفلام الخيال العلمي Studio HD</h3>
                    <span class="cat-tag">أفلام • MP4 1080p</span>
                </div>
                <button class="play-btn">تشغيل البث</button>
            </div>

            <!-- Channel 6: Sports Action MP4 Stream -->
            <div class="channel-card" data-category="رياضية" data-stream="https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/WeAreGoingOnBullrun.mp4" data-backup="https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8" onclick="playChannel('قناة المحركات والسباقات GT', 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/WeAreGoingOnBullrun.mp4', 'رياضية')">
                <img src="https://picsum.photos/seed/motors/120/120" alt="قناة المحركات والسباقات GT" class="channel-logo">
                <div class="channel-info">
                    <h3>قناة المحركات والسباقات GT</h3>
                    <span class="cat-tag">رياضية • HD Stream</span>
                </div>
                <button class="play-btn">تشغيل البث</button>
            </div>
        </section>
    </main>
    <script src="app.js"></script>
</body>
</html>
    """.trimIndent()

    val ARABIC_SPORTS_NEWS_IPTV_CSS = """
body {
    margin: 0;
    padding: 0;
    background-color: #090D16;
    color: #F8FAFC;
    font-family: system-ui, -apple-system, sans-serif;
}
.top-header {
    background: linear-gradient(135deg, #111827, #1E1B4B);
    padding: 16px;
    border-bottom: 1px solid #334155;
}
.brand {
    display: flex;
    align-items: center;
    gap: 10px;
}
.brand h1 {
    font-size: 18px;
    margin: 0;
    color: #00E5FF;
}
.live-dot {
    width: 10px;
    height: 10px;
    background-color: #EF4444;
    border-radius: 50%;
    box-shadow: 0 0 8px #EF4444;
}
.category-tabs {
    display: flex;
    gap: 8px;
    margin-top: 12px;
    overflow-x: auto;
}
.tab {
    background: #1E293B;
    color: #CBD5E1;
    border: 1px solid #334155;
    padding: 6px 14px;
    border-radius: 20px;
    cursor: pointer;
    font-size: 13px;
}
.tab.active {
    background: #00E5FF;
    color: #001F24;
    font-weight: bold;
}
.container {
    padding: 16px;
}
.channels-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
    gap: 12px;
    margin-top: 16px;
}
.channel-card {
    background: #111827;
    border: 1px solid #1E293B;
    border-radius: 12px;
    padding: 12px;
    display: flex;
    align-items: center;
    gap: 12px;
    transition: transform 0.2s, border-color 0.2s;
}
.channel-card:hover {
    border-color: #00E5FF;
}
.channel-logo {
    width: 52px;
    height: 52px;
    border-radius: 10px;
    object-fit: cover;
}
.channel-info {
    flex: 1;
}
.channel-info h3 {
    margin: 0 0 4px 0;
    font-size: 15px;
}
.cat-tag {
    font-size: 11px;
    color: #00E5FF;
}
.play-btn {
    background: #7C4DFF;
    color: white;
    border: none;
    padding: 8px 12px;
    border-radius: 8px;
    font-weight: bold;
    cursor: pointer;
}
    """.trimIndent()

    val ARABIC_SPORTS_NEWS_IPTV_JS = """
function playChannel(title, streamUrl, category) {
    console.log("Playing channel:", title, streamUrl);
    var titleEl = document.getElementById('current-channel-title');
    if (titleEl) {
        titleEl.innerText = title + ' (' + category + ')';
    }
    // If Native Android Media3 Bridge is injected, route directly to ExoPlayer 1.5.1!
    if (window.AndroidNativePlayer && window.AndroidNativePlayer.openNativeExoPlayer) {
        window.AndroidNativePlayer.openNativeExoPlayer(title, streamUrl, category);
        return;
    }
    var video = document.getElementById('html5-video-player');
    if (video) {
        video.src = streamUrl;
        video.play();
    }
}

function filterCategory(cat) {
    var cards = document.querySelectorAll('.channel-card');
    cards.forEach(function(card) {
        if (cat === 'الكل' || card.getAttribute('data-category') === cat) {
            card.style.display = 'flex';
        } else {
            card.style.display = 'none';
        }
    });
}
    """.trimIndent()

    val CINEMA_VOD_JSON_HTML = """
<!DOCTYPE html>
<html lang="ar" dir="rtl">
<head>
    <meta charset="UTF-8">
    <title>تطبيق سينما بلس للأفلام والمسلسلات</title>
</head>
<body style="background:#0B0F19; color:#fff; font-family:sans-serif; padding:16px;">
    <h1 style="color:#A855F7;">🎬 سينما بلس VOD & Series</h1>
    <p>منصة مشاهدة الأفلام والمسلسلات بدقة عالية عبر مشغل Media3 ExoPlayer الأصلي</p>
    <div id="movies-list">
        <div class="channel-card" data-category="أفلام أكشن" data-stream="https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4" onclick="playMovie('فيلم دموع الفولاذ Tears of Steel 1080p', 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4')">
            <img src="https://picsum.photos/seed/movie1/120/160" alt="دموع الفولاذ" />
            <h3>فيلم دموع الفولاذ Tears of Steel 1080p</h3>
            <button>مشاهدة الفيلم الآن</button>
        </div>
        <div class="channel-card" data-category="أنيميشن" data-stream="https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4" onclick="playMovie('فيلم Big Buck Bunny FHD', 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4')">
            <img src="https://picsum.photos/seed/movie2/120/160" alt="Big Buck Bunny" />
            <h3>فيلم Big Buck Bunny FHD</h3>
            <button>مشاهدة الفيلم الآن</button>
        </div>
        <div class="channel-card" data-category="بث مباشر" data-stream="https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8" onclick="playMovie('قناة سينما لايف M3U8', 'https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8')">
            <img src="https://picsum.photos/seed/movie3/120/160" alt="قناة سينما لايف" />
            <h3>قناة سينما لايف M3U8</h3>
            <button>مشاهدة البث</button>
        </div>
    </div>
    <script>
        const apiEndpoint = "https://api.example.com/v1/movies.json";
        function playMovie(name, url) {
            if (window.AndroidNativePlayer) {
                window.AndroidNativePlayer.openNativeExoPlayer(name, url, "أفلام ومسلسلات");
            }
        }
    </script>
</body>
</html>
    """.trimIndent()
}
