"""
Downloader — Python модуль для yt-dlp
Викликається з Kotlin через Chaquopy
"""
import yt_dlp
import os
import json
import traceback


def detect_platform(url: str) -> str:
    u = url.lower()
    if 'youtube.com' in u or 'youtu.be' in u or 'music.youtube' in u:
        return 'youtube'
    if 'tiktok.com' in u:
        return 'tiktok'
    if 'instagram.com' in u or 'instagr.am' in u:
        return 'instagram'
    if 'twitter.com' in u or 'x.com' in u or 't.co' in u:
        return 'twitter'
    if 'twitch.tv' in u:
        return 'twitch'
    if 'soundcloud.com' in u:
        return 'soundcloud'
    if 'vimeo.com' in u:
        return 'vimeo'
    if 'facebook.com' in u or 'fb.com' in u or 'fb.watch' in u:
        return 'facebook'
    return 'unknown'


def get_format_string(fmt: str, quality: str) -> str:
    if fmt == 'audio':
        # Качаємо тільки аудіо потік, потім перейменуємо на .mp3
        return 'bestaudio[ext=m4a]/bestaudio'

    q_map = {'1080': 1080, '720': 720, '480': 480}
    max_h = q_map.get(quality, 9999)

    if max_h == 9999:
        return 'bestvideo+bestaudio/best'
    return f'bestvideo[height<={max_h}]+bestaudio/best[height<={max_h}]/best[height<={max_h}]'


def download(url: str, fmt: str, quality: str, output_dir: str) -> str:
    """
    Завантажує відео/аудіо.
    fmt: 'video' | 'audio'
    quality: 'best' | '1080' | '720' | '480'
    """
    platform = detect_platform(url)
    format_str = get_format_string(fmt, quality)

    outtmpl = os.path.join(output_dir, '%(title).80s.%(ext)s')

    opts = {
        'format': format_str,
        'outtmpl': outtmpl,
        'quiet': True,
        'no_warnings': True,
        'noplaylist': True,
        # Без postprocessors — не потрібен ffmpeg
    }

    if fmt == 'video':
        opts['merge_output_format'] = 'mp4'

    # YouTube — обходимо JS runtime
    if platform == 'youtube':
        opts['extractor_args'] = {'youtube': {'player_client': ['android', 'web']}}

    downloaded_files = []

    def progress_hook(d):
        if d['status'] == 'finished':
            downloaded_files.append(d.get('filename', ''))

    opts['progress_hooks'] = [progress_hook]

    try:
        with yt_dlp.YoutubeDL(opts) as ydl:
            info = ydl.extract_info(url, download=True)
            title = info.get('title', 'Без назви')

        # Шукаємо збережений файл
        file_path = ''
        if downloaded_files:
            candidate = downloaded_files[-1]
            # Для відео перевіряємо чи є mp4 версія
            if fmt == 'video':
                mp4 = os.path.splitext(candidate)[0] + '.mp4'
                file_path = mp4 if os.path.exists(mp4) else candidate
            else:
                # Аудіо — перейменовуємо на .mp3
                mp3_path = os.path.splitext(candidate)[0] + '.mp3'
                if os.path.exists(candidate):
                    os.rename(candidate, mp3_path)
                file_path = mp3_path

        # Fallback — найновіший файл у папці
        if not file_path or not os.path.exists(file_path):
            exts = ['mp4', 'webm', 'mkv'] if fmt == 'video' else ['mp3', 'm4a', 'webm', 'opus']
            all_files = []
            for f in os.listdir(output_dir):
                if any(f.endswith(f'.{e}') for e in exts):
                    full = os.path.join(output_dir, f)
                    all_files.append((os.path.getmtime(full), full))
            if all_files:
                file_path = sorted(all_files)[-1][1]

        if not file_path or not os.path.exists(file_path):
            return json.dumps({'success': False, 'error': 'Файл не знайдено після завантаження'})

        file_size = os.path.getsize(file_path)
        file_ext = os.path.splitext(file_path)[1].lstrip('.')

        return json.dumps({
            'success': True,
            'title': title,
            'platform': platform,
            'file_path': file_path,
            'file_size': file_size,
            'format': file_ext,
            'quality': quality,
        })

    except yt_dlp.utils.DownloadError as e:
        err = str(e)
        msg = (
            'Відео недоступне у вашому регіоні' if 'not available' in err
            else 'Приватне відео' if 'Private' in err
            else 'Відео не знайдено' if 'does not exist' in err
            else f'Помилка завантаження: {err[:300]}'
        )
        return json.dumps({'success': False, 'error': msg})
    except Exception as e:
        return json.dumps({
            'success': False,
            'error': f'Помилка: {str(e)}',
            'traceback': traceback.format_exc()[-500:]
        })
