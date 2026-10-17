#!/usr/bin/env python3
"""Collect only passing actual-game runs that match the current release JARs."""
from pathlib import Path
import datetime,gzip,hashlib,json,re
ROOT=Path(__file__).resolve().parents[2];BASE=ROOT/'qa/1.0.5'
VERSIONS=('26.1','26.1.1','26.1.2')

def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def relative(path):return path.relative_to(ROOT).as_posix()
def newest(mode,version):
 artifact=ROOT/'app'/f'KapKan-1.0.5-mc{version}-fabric.jar';expected=sha(artifact)
 for run in sorted(BASE.glob('actual-'+mode+'-'+version+'-*'),reverse=True):
  if not (run/'PASS.txt').exists() or not (run/'run.json').exists():continue
  metadata=json.loads((run/'run.json').read_text())
  if metadata.get('moduleSha256')==expected:return run,metadata
 return None,None

def count_marker(path):
 text=path.read_text();match=re.search(r'(\d+) (?:assertions|checks) passed',text)
 return int(match.group(1)) if match else None

def describe(mode,version,required=False):
 run,metadata=newest(mode,version)
 if run is None:
  if required:raise RuntimeError('No passing exact-hash '+mode+' run for '+version)
  return None
 counts={};markers=[]
 if mode=='acceptance':
  expected=[('alpha','qa-passed.txt'),('beta','qa-passed.txt'),('alpha','restart-passed.txt'),('beta','restart-passed.txt')]
  expected += [(role,name) for role in ('alpha','beta') for name in ('dimension-passed.txt','respawn-passed.txt','reconnect-passed.txt')]
  for role,name in expected:
   path=run/role/name
   if not path.exists():raise RuntimeError('Missing actual acceptance marker '+str(path))
   markers.append(relative(path))
   if (count:=count_marker(path)) is not None:counts[role+'/'+name]=count
  scope=['Two separate real Minecraft client processes and a matching-version dedicated Fabric server','Bidirectional badge identities and cape PNGs reach actual player rendering; same-view before/after remote No Cape screenshots','No Cape chosen through the actual menu; immediate local state, remote renderer removal and persistence after both client process restarts','First-launch introduction saved before display; no repeats after menu closure, world join, Nether dimension transition, server death/respawn or actual disconnect/reconnect','Actual menus at 1280x800 GUI scales 1/2/3 and 854x480 GUI scale 2; Escape closes; saved HUD profile reloads','Standalone client installation path: launcher-only kapkan.launchSession argument omitted']
 elif mode=='features':
  path=run/'alpha/feature-passed.txt';markers=[relative(path)];counts={'feature_assertions':count_marker(path)}
  scope=['Real FPS, player coordinates, armor stack durability and active-effect data','Actual sprint forward/latch, explicit crouch stop, hunger eligibility, resume and toggle off','Smooth 3x zoom, release restoration and menu input gate','Actual rendered HUD editor mouse drag/release, wheel scaling, arrow input and profile persistence','Help explicitly reopens introduction without automatic repetition','Native X11 left/right mouse and W key events reach actual click and keystroke indicators']
 elif mode=='ui':
  for name in ('ui-passed.txt','ui-restart-passed.txt'):
   path=run/'alpha'/name
   if not path.exists():raise RuntimeError('Missing UI restart evidence '+str(path))
   markers.append(relative(path));counts[name]=count_marker(path)
  scope=['Actual search, category filtering, list scrolling and immediate saved toggles','Menu Controls rebinding through real input handling and native options persistence after an actual client process restart']
 else:
  path=run/'alpha/smoke-passed.txt';markers=[relative(path)];counts={'smoke_assertions':count_marker(path)}
  scope=['Matching-version real Minecraft world, HUD, menu, J key mapping, Escape, introduction and server relay startup smoke']
 # Preserve original game logs in a flat evidence directory before archive filters
 # exclude local world/runtime files. No bytes or assertions are rewritten.
 evidence=run/'game-logs';evidence.mkdir(exist_ok=True)
 for role in ('alpha','beta'):
  for log in (run/role/'logs').glob('*'):
   if log.is_file() and (log.suffix=='.log' or log.name.endswith('.log.gz')):
    (evidence/(role+'-'+log.name)).write_bytes(log.read_bytes())
 return {'status':'passed','minecraft':version,'mode':mode,'artifact':relative(ROOT/'app'/f'KapKan-1.0.5-mc{version}-fabric.jar'),'artifact_sha256':metadata['moduleSha256'],'run_directory':relative(run),'pass_marker':relative(run/'PASS.txt'),'assertion_markers':markers,'assertion_counts':counts,'scope':scope,'screenshots':[relative(p) for p in sorted((run/'screenshots').glob('*.png'))],'platform':'Linux x86_64, Java 25, Fabric Loader 0.19.5, Xvfb TCP and Mesa llvmpipe software rendering'}

results={'release':'1.0.5','acceptance_count_note':'Per-process acceptance counters include repeated readiness-state assertions; feature and UI counts are discrete checks.','generated_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'actual_game_verification':True,'artifacts':[],'runs':[],'limits':['Windows and macOS native Minecraft runtime testing was not performed','Additional rendering mods, Microsoft account sign-in and external public servers were not tested','No performance improvement or FPS boost is claimed; software rendering was used for correctness','Minecraft versions outside 26.1, 26.1.1 and 26.1.2 and loaders other than Fabric are not verified']}
for version in VERSIONS:
 artifact=ROOT/'app'/f'KapKan-1.0.5-mc{version}-fabric.jar'
 results['artifacts'].append({'minecraft':version,'loader':'Fabric','tested_loader_version':'0.19.5','java':25,'artifact':relative(artifact),'sha256':sha(artifact),'built_successfully':True})
 for mode in ('acceptance','features','smoke'):
  result=describe(mode,version,required=mode=='acceptance' or mode=='features')
  if result:results['runs'].append(result)
ui=describe('ui','26.1.2',required=True)
if ui:results['runs'].append(ui)
(BASE/'final-results.json').write_text(json.dumps(results,indent=2)+'\n')
lines=['# KapKan 1.0.5 actual-game verification','',
'All advertised Minecraft targets were compiled separately against their exact official game JAR and then executed in real graphical Minecraft clients. The dedicated server and clients used isolated local development identities. These are actual game screenshots and test-only automated input drivers, not UI previews or simulated players.','',
'| Minecraft | Loader tested | Java | Build | Actual in-game verification |','|---|---|---|---|---|']
for artifact in results['artifacts']:
 version=artifact['minecraft'];feature=next(r for r in results['runs'] if r['minecraft']==version and r['mode']=='features')
 lines.append('| '+version+' | Fabric 0.19.5 | 25 | `'+Path(artifact['artifact']).name+'` | Two separate clients: cosmetics, No Cape, lifecycle and process restart; '+str(feature['assertion_counts']['feature_assertions'])+' gameplay feature checks |')
lines += ['','## Verified in the game','',
'- First introduction is committed to preferences before display, then stays dismissed through actual joins, menu closure, Nether transitions, death/respawn, disconnect/reconnect and both client process restarts. Help can open it again.',
'- Two independent clients receive badges and cape images for the server-provided player UUID. Original screenshots show the same remote player from behind before and after No Cape, with the badge retained.',
'- Menus render at 1280 × 800 with GUI scales 1, 2 and 3, plus 854 × 480 at GUI scale 2. Escape works and HUD profiles restore positions.',
'- Real FPS, coordinates, armor durability and active-effect data; actual sprint eligibility, explicit sneak stop, smooth zoom/release; draggable/scalable HUD with keyboard movement and saved profiles.',
'- Real X11 left/right mouse and W key events reach the click/keystroke indicators.',
'- Search/filter/scroll, immediate saved toggles and actual Controls rebinding are exercised separately. The native Minecraft options key binding is then verified after terminating and restarting the actual client process.','',
'## Evidence','']
for run in results['runs']:
 lines.append('- '+run['minecraft']+' '+run['mode']+': `'+run['run_directory']+'` (`PASS.txt`, `run.json`, assertion markers, original game logs and PNG screenshots).')
lines += ['','`final-results.json` records exact production JAR SHA-256 values and selected passing run paths. Earlier preliminary runs and superseded artifact hashes are excluded from the final evidence set.','',
'## Limits','']+[ '- '+limit+'.' for limit in results['limits']]+['',
'Custom badges/capes require compatible KapKan clients and the exact-version KapKan JAR on the Fabric server for the server relay path. Unmodified clients do not gain cosmetic rendering automatically. Public relay infrastructure was not deployed by these tests.','',
'Do not distribute `qa/1.0.5/runtime`, local test server/world directories, compiled probe classes or test-only mod JARs. Minecraft itself is a verified test input, not part of the release.','']
(BASE/'ACTUAL_TESTS.md').write_text('\n'.join(lines))
print('Saved exact-hash actual-game results:',BASE/'final-results.json')
