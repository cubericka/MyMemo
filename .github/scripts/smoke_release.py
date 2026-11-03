"""Exercise the signed release on a fresh CI emulator; never use a user's device."""
import json
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

APP = 'com.cubericka.mymemo.titledrag'
ACTIVITY = APP + '/com.personal.memostarter.MainActivity'
OUT = Path('release-checks')
OUT.mkdir(exist_ok=True)
checks = []

def adb(*args):
    return subprocess.check_output(['adb', *args], text=True, stderr=subprocess.STDOUT)

def ui():
    adb('shell', 'uiautomator', 'dump', '/sdcard/mymemo-window.xml')
    return ET.fromstring(adb('exec-out', 'cat', '/sdcard/mymemo-window.xml'))

def wait_for(attribute, value):
    for _ in range(12):
        root = ui()
        nodes = [n for n in root.iter('node') if n.get(attribute) == value]
        if nodes:
            return nodes[0]
        time.sleep(1)
    raise AssertionError('Missing UI: ' + value)

def tap(node):
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.get('bounds')))
    adb('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))

def click(attribute, value):
    tap(wait_for(attribute, value))

def fill(index, value):
    fields = [n for n in ui().iter('node') if n.get('class') == 'android.widget.EditText']
    tap(fields[index])
    adb('shell', 'input', 'text', value)

try:
    result = adb('install', '-r', 'MyMemo-3.4.2-release.apk')
    assert 'Success' in result
    checks.append('signed_release_install')
    package = adb('shell', 'dumpsys', 'package', APP)
    assert 'versionCode=361' in package and 'versionName=3.6.1' in package
    flags = re.findall(r'(?:pkgFlags|flags)=\[([^\]]*)\]', package)
    assert flags and all('DEBUGGABLE' not in f for f in flags)
    checks.append('release_version_and_non_debuggable')
    adb('shell', 'am', 'start', '-W', '-n', ACTIVITY)
    wait_for('content-desc', '새 메모')
    wait_for('content-desc', '앱 잠금 설정')
    checks.append('installed_integrity_and_main_pin_entry')
    click('content-desc', '새 메모')
    wait_for('content-desc', '편집 메뉴')
    fill(0, 'Release361Smoke')
    fill(1, 'SavedReleaseMemo')
    adb('shell', 'input', 'keyevent', '4')
    click('content-desc', '편집 메뉴')
    root = ui()
    texts = {n.get('text') for n in root.iter('node')}
    assert '앱 잠금 설정' not in texts
    assert {'메모 정보','상단 고정','다른 위치로 이동','휴지통으로'} <= texts
    checks.append('memo_menu_has_no_app_lock_and_keeps_actions')
    click('text', '메모 정보')
    click('text', '확인')
    click('text', '저장')
    click('content-desc', '저장하고 목록으로')
    wait_for('text', 'Release361Smoke')
    checks.append('memo_creation_and_save')
    click('content-desc', '앱 잠금 설정')
    click('text', '앱 PIN 설정')
    fill(0, '7284')
    fill(1, '7284')
    adb('shell', 'input', 'keyevent', '4')
    click('text', '잠금 설정')
    wait_for('text', '지금 잠그기')
    checks.append('app_pin_setup_with_android_keystore')
    adb('shell', 'am', 'force-stop', APP)
    adb('shell', 'am', 'start', '-W', '-n', ACTIVITY)
    wait_for('text', '앱 PIN 4자리')
    assert not any(n.get('text') == 'Release361Smoke' for n in ui().iter('node'))
    fill(0, '7284')
    adb('shell', 'input', 'keyevent', '4')
    click('text', '앱 잠금 해제')
    wait_for('text', 'Release361Smoke')
    checks.append('pin_and_memo_persist_after_process_restart')
    click('text', 'Release361Smoke')
    wait_for('text', '읽기 모드')
    wait_for('text', 'SavedReleaseMemo')
    checks.append('saved_memo_reopens_after_unlock')
finally:
    (OUT/'emulator-results.json').write_text(json.dumps({'api':34,'environment':'Android emulator, not physical device','passed_checks':checks},ensure_ascii=False,indent=2)+'\n')
    (OUT/'logcat.txt').write_text(adb('logcat','-d','-s','AndroidRuntime:E'))
print(json.dumps({'passed': len(checks), 'checks': checks}))
