"""
Downloader — Python модуль для yt-dlp
"""
import yt_dlp
import os
import json
import shutil
import traceback
import threading

# Глобальний флаг скасування
_cancel_flag = threading.Event()

def cancel():
    """Викликається з Kotlin для зупинки завантаження"""
    _cancel_flag.set()

def reset_cancel():
    _cancel_flag.clear()

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


def unique_path(path: str) -> str:
    """Якщо файл вже існує — додає (1), (2) тощо перед розширенням"""
    if not os.path.exists(path):
        return path
    dot = path.rfind('.')
    if dot == -1:
        base, ext = path, ''
    else:
        base, ext = path[:dot], path[dot:]
    counter = 1
    while True:
        candidate = f"{base} ({counter}){ext}"
        if not os.path.exists(candidate):
            return candidate
        counter += 1


def download(url: str, fmt: str, quality: str, output_dir: str) -> str:
    """
    fmt: 'video' | 'audio'
    quality: 'best' | '1080' | '720' | '480'
    """
    reset_cancel()
    platform = detect_platform(url)

    # Формат — аудіо і відео качаємо однаково (bestvideo+bestaudio/best)
    # Для аудіо просто перейменуємо mp4 → mp3 після завантаження
    q_map = {'1080': 1080, '720': 720, '480': 480}
    max_h = q_map.get(quality, 9999)

    if max_h == 9999:
        format_str = 'bestvideo+bestaudio/best'
    else:
        format_str = f'bestvideo[height<={max_h}]+bestaudio/best[height<={max_h}]/best[height<={max_h}]'

    outtmpl = os.path.join(output_dir, '%(title).80s.%(ext)s')

    opts = {
        'format': format_str,
        'outtmpl': outtmpl,
        'quiet': True,
        'no_warnings': True,
        'noplaylist': True,
        'merge_output_format': 'mp4',
    }

    if platform == 'youtube':
        opts['extractor_args'] = {'youtube': {'player_client': ['android', 'web']}}

    downloaded_files = []

    def progress_hook(d):
        # Перевіряємо флаг скасування
        if _cancel_flag.is_set():
            raise yt_dlp.utils.DownloadError('Скасовано користувачем')
        if d['status'] == 'finished':
            downloaded_files.append(d.get('filename', ''))

    opts['progress_hooks'] = [progress_hook]

    try:
        with yt_dlp.YoutubeDL(opts) as ydl:
            info = ydl.extract_info(url, download=True)
            title = info.get('title', 'Без назви')

        if _cancel_flag.is_set():
            # Видаляємо частково скачаний файл
            for f in downloaded_files:
                try:
                    if os.path.exists(f):
                        os.remove(f)
                except:
                    pass
            return json.dumps({'success': False, 'error': 'Скасовано'})

        # Знаходимо скачаний файл
        file_path = ''
        if downloaded_files:
            candidate = downloaded_files[-1]
            dot = candidate.rfind('.')
            mp4 = (candidate[:dot] + '.mp4') if dot != -1 else candidate
            src = mp4 if os.path.exists(mp4) else candidate

            if fmt == 'audio':
                # Перейменовуємо mp4 → mp3
                dot2 = src.rfind('.')
                mp3_path = (src[:dot2] + '.mp3') if dot2 != -1 else src + '.mp3'
                mp3_path = unique_path(mp3_path)
                if os.path.exists(src):
                    shutil.copy2(src, mp3_path)
                    try: os.remove(src)
                    except: pass
                file_path = mp3_path
            else:
                # Відео — перевіряємо дублікати
                final = unique_path(src)
                if final != src and os.path.exists(src):
                    shutil.move(src, final)
                file_path = final if os.path.exists(final) else src

        # Fallback
        if not file_path or not os.path.exists(file_path):
            exts = ['mp3'] if fmt == 'audio' else ['mp4', 'webm', 'mkv']
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
        file_ext = file_path[file_path.rfind('.')+1:] if '.' in file_path else 'mp4'

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
        if 'Скасовано' in err:
            return json.dumps({'success': False, 'error': 'Скасовано'})
        msg = (
            'Відео недоступне у вашому регіоні' if 'not available' in err
            else 'Приватне відео' if 'Private' in err
            else 'Відео не знайдено' if 'does not exist' in err
            else f'Помилка завантаження: {err[:300]}'
        )
        return json.dumps({'success': False, 'error': msg})
    except Exception as e:
        return json.dumps({'success': False, 'error': f'Помилка: {str(e)[:200]}'})
