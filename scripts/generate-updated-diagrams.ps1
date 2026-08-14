#requires -Version 7.0

[CmdletBinding()]
param(
    [string]$RepositoryRoot = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = 'Stop'
$RepositoryRoot = [IO.Path]::GetFullPath($RepositoryRoot)
$OutputRoot = Join-Path $RepositoryRoot 'Diagram\diagram update'
$script:DefaultEdgeStyle = 'edgeStyle=orthogonalEdgeStyle;rounded=0;orthogonalLoop=1;jettySize=auto;html=1;endArrow=block;'

function New-Cell {
    param(
        [string]$Id,
        [string]$Value,
        [double]$X,
        [double]$Y,
        [double]$Width,
        [double]$Height,
        [string]$Style
    )

    $Value = $Value.Replace('`n', [Environment]::NewLine)
    $cell = [Xml.Linq.XElement]::new([Xml.Linq.XName]::Get('mxCell'))
    $cell.SetAttributeValue('id', $Id)
    $cell.SetAttributeValue('value', $Value)
    $cell.SetAttributeValue('style', $Style)
    $cell.SetAttributeValue('vertex', '1')
    $cell.SetAttributeValue('parent', '1')
    $geometry = [Xml.Linq.XElement]::new([Xml.Linq.XName]::Get('mxGeometry'))
    $geometry.SetAttributeValue('x', $X)
    $geometry.SetAttributeValue('y', $Y)
    $geometry.SetAttributeValue('width', $Width)
    $geometry.SetAttributeValue('height', $Height)
    $geometry.SetAttributeValue('as', 'geometry')
    $cell.Add($geometry)
    return $cell
}

function New-Edge {
    param(
        [string]$Id,
        [string]$Source,
        [string]$Target,
        [string]$Value = '',
        [string]$Style = $script:DefaultEdgeStyle
    )

    $cell = [Xml.Linq.XElement]::new([Xml.Linq.XName]::Get('mxCell'))
    $cell.SetAttributeValue('id', $Id)
    $cell.SetAttributeValue('value', $Value)
    $cell.SetAttributeValue('style', $Style)
    $cell.SetAttributeValue('edge', '1')
    $cell.SetAttributeValue('parent', '1')
    $cell.SetAttributeValue('source', $Source)
    $cell.SetAttributeValue('target', $Target)
    $geometry = [Xml.Linq.XElement]::new([Xml.Linq.XName]::Get('mxGeometry'))
    $geometry.SetAttributeValue('relative', '1')
    $geometry.SetAttributeValue('as', 'geometry')
    $cell.Add($geometry)
    return $cell
}

function New-Page {
    param(
        [string]$Id,
        [string]$Name,
        [object[]]$Cells,
        [int]$PageWidth = 1800,
        [int]$PageHeight = 1200
    )

    $root = [Xml.Linq.XElement]::new([Xml.Linq.XName]::Get('root'))
    $zero = [Xml.Linq.XElement]::new([Xml.Linq.XName]::Get('mxCell'))
    $zero.SetAttributeValue('id', '0')
    $one = [Xml.Linq.XElement]::new([Xml.Linq.XName]::Get('mxCell'))
    $one.SetAttributeValue('id', '1')
    $one.SetAttributeValue('parent', '0')
    $root.Add($zero, $one)
    foreach ($cell in $Cells) {
        $root.Add($cell)
    }

    $model = [Xml.Linq.XElement]::new([Xml.Linq.XName]::Get('mxGraphModel'))
    $model.SetAttributeValue('dx', '1422')
    $model.SetAttributeValue('dy', '794')
    $model.SetAttributeValue('grid', '1')
    $model.SetAttributeValue('gridSize', '10')
    $model.SetAttributeValue('guides', '1')
    $model.SetAttributeValue('tooltips', '1')
    $model.SetAttributeValue('connect', '1')
    $model.SetAttributeValue('arrows', '1')
    $model.SetAttributeValue('fold', '1')
    $model.SetAttributeValue('page', '1')
    $model.SetAttributeValue('pageScale', '1')
    $model.SetAttributeValue('pageWidth', $PageWidth)
    $model.SetAttributeValue('pageHeight', $PageHeight)
    $model.SetAttributeValue('math', '0')
    $model.SetAttributeValue('shadow', '0')
    $model.Add($root)

    $diagram = [Xml.Linq.XElement]::new([Xml.Linq.XName]::Get('diagram'))
    $diagram.SetAttributeValue('id', $Id)
    $diagram.SetAttributeValue('name', $Name)
    $diagram.Add($model)
    return $diagram
}

function Write-MxFile {
    param([string]$Name, [object[]]$Pages)

    $mxfile = [Xml.Linq.XElement]::new([Xml.Linq.XName]::Get('mxfile'))
    $mxfile.SetAttributeValue('host', 'app.diagrams.net')
    $mxfile.SetAttributeValue('modified', (Get-Date).ToString('o'))
    $mxfile.SetAttributeValue('agent', 'VibeGraph evidence generator')
    $mxfile.SetAttributeValue('version', '24.7.17')
    foreach ($page in $Pages) {
        $mxfile.Add($page)
    }
    $document = [Xml.Linq.XDocument]::new([Xml.Linq.XDeclaration]::new('1.0', 'UTF-8', $null), $mxfile)
    [IO.File]::WriteAllText((Join-Path $OutputRoot $Name), $document.ToString(), [Text.UTF8Encoding]::new($false))
}

function New-Title([string]$Text, [int]$Width = 1500) {
    return New-Cell 'title' $Text 80 30 $Width 55 'text;html=1;strokeColor=none;fillColor=none;fontSize=24;fontStyle=1;align=left;verticalAlign=middle;'
}

function New-Note([string]$Id, [string]$Text, [double]$X, [double]$Y, [double]$Width, [double]$Height) {
    return New-Cell $Id $Text $X $Y $Width $Height 'shape=note;whiteSpace=wrap;html=1;fillColor=#fffbea;strokeColor=#ca8a04;fontSize=12;align=left;verticalAlign=top;spacing=8;'
}

function New-Actor([string]$Id, [string]$Text, [double]$X, [double]$Y) {
    return New-Cell $Id $Text $X $Y 110 120 'shape=umlActor;verticalLabelPosition=bottom;verticalAlign=top;html=1;fontSize=14;'
}

function New-UseCase([string]$Id, [string]$Text, [double]$X, [double]$Y, [double]$Width = 300, [double]$Height = 90) {
    return New-Cell $Id $Text $X $Y $Width $Height 'ellipse;whiteSpace=wrap;html=1;fillColor=#ecfeff;strokeColor=#0891b2;fontSize=14;spacing=6;'
}

function New-Step([string]$Id, [string]$Text, [double]$X, [double]$Y, [double]$Width = 360, [double]$Height = 80, [string]$Fill = '#eff6ff', [string]$Stroke = '#2563eb') {
    return New-Cell $Id $Text $X $Y $Width $Height "rounded=1;whiteSpace=wrap;html=1;fillColor=$Fill;strokeColor=$Stroke;fontSize=14;spacing=7;"
}

function New-Decision([string]$Id, [string]$Text, [double]$X, [double]$Y, [double]$Width = 180, [double]$Height = 110) {
    return New-Cell $Id $Text $X $Y $Width $Height 'rhombus;whiteSpace=wrap;html=1;fillColor=#fff7ed;strokeColor=#ea580c;fontSize=13;spacing=5;'
}

function Add-EdgeSequence([Collections.ArrayList]$Cells, [string[]]$Ids, [string]$Prefix) {
    for ($i = 0; $i -lt $Ids.Count - 1; $i++) {
        [void]$Cells.Add((New-Edge "$Prefix-$i" $Ids[$i] $Ids[$i + 1]))
    }
}

# Use-case pages retain the original 10-page review inventory.
$useCasePages = [Collections.ArrayList]::new()

$cells = [Collections.ArrayList]::new()
[void]$cells.Add((New-Title 'VibeGraph - Current System Boundary'))
$actors = @(
    @('guest','Guest',80,160), @('user','Authenticated User',80,360), @('admin','Admin',80,560),
    @('ai','AI Coding Client',1550,180), @('cli','CLI / API-key Client',1550,390),
    @('system','File Events / Workers',1550,600)
)
foreach ($a in $actors) { [void]$cells.Add((New-Actor $a[0] $a[1] $a[2] $a[3])) }
$ucs = @(
    @('auth','Authenticate / maintain session',430,120), @('account','Manage account / API keys',850,120),
    @('projects','Import / manage projects',430,290), @('analyze','Analyze source',850,290),
    @('explore','Explore graph / source / impact',430,460), @('broadcast','Broadcast graph updates',850,460),
    @('uml','Generate use-case UML view',430,630), @('mcp','Call MCP tools',850,630),
    @('adminops','Operate admin / security console',640,800)
)
foreach ($u in $ucs) { [void]$cells.Add((New-UseCase $u[0] $u[1] $u[2] $u[3] 330 95)) }
$links = @(
    @('guest','auth'), @('user','auth'), @('user','account'), @('user','projects'), @('user','analyze'),
    @('user','explore'), @('user','uml'), @('admin','adminops'), @('ai','mcp'), @('cli','projects'),
    @('system','analyze'), @('system','broadcast')
)
$i = 0; foreach ($link in $links) { [void]$cells.Add((New-Edge "e$i" $link[0] $link[1])); $i++ }
[void]$cells.Add((New-Note 'note' 'Evidence: AuthController; ProjectController; LocalPatchController; FileChangeBroadcaster; DiagramController; McpServerConfig. Manual analysis is user-triggered; workers execute queued/import/watcher work.' 300 960 1180 100))
[void]$useCasePages.Add((New-Page 'uc-general' 'Use Case Tổng quát' $cells 1800 1150))

$cells = [Collections.ArrayList]::new()
[void]$cells.Add((New-Title 'Authentication and Account'))
[void]$cells.Add((New-Actor 'guest' 'Guest' 80 260)); [void]$cells.Add((New-Actor 'user' 'User' 80 650))
$authCases = @(
    @('register','Register new account',360,120), @('login','Authenticate existing account',750,120),
    @('oauth','Google / GitHub OAuth2 callback',1140,120), @('refresh','Rotate refresh session',360,330),
    @('logout','Revoke session and clear cookies',750,330), @('me','Read current user',1140,330),
    @('profile','Update profile / password',360,570), @('keys','Manage API keys',750,570),
    @('self','Read usage / ledger / reports / notifications',1140,570)
)
foreach ($u in $authCases) { [void]$cells.Add((New-UseCase $u[0] $u[1] $u[2] $u[3] 320 95)) }
foreach ($target in @('register','login','oauth')) { [void]$cells.Add((New-Edge "g-$target" 'guest' $target)) }
foreach ($target in @('refresh','logout','me','profile','keys','self')) { [void]$cells.Add((New-Edge "u-$target" 'user' $target)) }
foreach ($source in @('register','login','oauth')) { [void]$cells.Add((New-Edge "$source-session" $source 'refresh' 'creates session' 'edgeStyle=orthogonalEdgeStyle;dashed=1;html=1;endArrow=open;')) }
[void]$cells.Add((New-Note 'note' 'Register persists a new User; login authenticates an existing User. AuthController/OAuth2LoginSuccessHandler use AuthCookieService for cookie writes and clearing.' 330 810 1100 110))
[void]$useCasePages.Add((New-Page 'uc-auth' 'Use Case Đăng ký & Đăng nhập (Guest)' $cells))

$cells = [Collections.ArrayList]::new()
[void]$cells.Add((New-Title 'Project Lifecycle'))
[void]$cells.Add((New-Actor 'user' 'Authenticated User' 90 430))
$projectCases = @(
    @('crud','Create / list / get project',380,100), @('cli','Create CLI setup',800,100),
    @('analyze','Request manual analysis (202)',1220,100), @('trash','Move project to trash',380,330),
    @('listtrash','List trash',800,330), @('restore','Restore project',1220,330),
    @('purge','Permanently purge project',590,590), @('ownership','Enforce project ownership',1010,590)
)
foreach ($u in $projectCases) { [void]$cells.Add((New-UseCase $u[0] $u[1] $u[2] $u[3] 330 95)) }
foreach ($target in @('crud','cli','analyze','trash','listtrash','restore','purge')) { [void]$cells.Add((New-Edge "u-$target" 'user' $target)) }
foreach ($source in @('crud','analyze','trash','listtrash','restore','purge')) { [void]$cells.Add((New-Edge "$source-own" $source 'ownership' 'guard' 'edgeStyle=orthogonalEdgeStyle;dashed=1;html=1;endArrow=open;')) }
[void]$cells.Add((New-Note 'note' 'Evidence: ProjectController routes and V17__project_trash.sql. No generic update-project use case is claimed.' 350 830 1080 100))
[void]$useCasePages.Add((New-Page 'uc-project' 'Quản lý Project (User)' $cells))

$cells = [Collections.ArrayList]::new()
[void]$cells.Add((New-Title 'Project Import - Verified Response and Executor Topology'))
[void]$cells.Add((New-Actor 'user' 'User' 60 430))
[void]$cells.Add((New-UseCase 'archive' 'POST /api/projects/import-archive' 310 120 360 100))
[void]$cells.Add((New-UseCase 'sync' 'Default async=false: extract + analyze synchronously; return 200' 820 80 430 120))
[void]$cells.Add((New-UseCase 'archiveAsync' 'async=true: register, submit analysis, return 202' 820 250 430 120))
[void]$cells.Add((New-UseCase 'github' 'POST /api/projects/import-github' 310 420 360 100))
[void]$cells.Add((New-UseCase 'local' 'POST /api/projects/import-local / browse' 310 650 360 100))
[void]$cells.Add((New-UseCase 'executor' 'analysisExecutor runs AnalyzeService' 1330 390 360 110))
[void]$cells.Add((New-UseCase 'status' 'Mark ANALYZED / FAILED and broadcast status' 1330 620 360 110))
foreach ($target in @('archive','github','local')) { [void]$cells.Add((New-Edge "u-$target" 'user' $target)) }
[void]$cells.Add((New-Edge 'e-archive-sync' 'archive' 'sync' 'default'))
[void]$cells.Add((New-Edge 'e-archive-async' 'archive' 'archiveAsync' 'only async=true'))
[void]$cells.Add((New-Edge 'e-async-exec' 'archiveAsync' 'executor' 'submit before 202'))
[void]$cells.Add((New-Edge 'e-github-exec' 'github' 'executor' 'submit before 202'))
[void]$cells.Add((New-Edge 'e-local-exec' 'local' 'executor' 'submit before 202'))
[void]$cells.Add((New-Edge 'e-exec-status' 'executor' 'status'))
[void]$cells.Add((New-Note 'note' 'The synchronous archive branch never passes through the 202/executor use case. ImportController and the three import-service implementations are the evidence.' 460 870 1050 100))
[void]$useCasePages.Add((New-Page 'uc-import' 'Use Case Import Project - 3 luồng (User)' $cells))

$cells = [Collections.ArrayList]::new()
[void]$cells.Add((New-Title 'Source Analysis and Realtime Updates'))
[void]$cells.Add((New-Actor 'user' 'User' 80 300)); [void]$cells.Add((New-Actor 'system' 'File Events / Workers' 80 650))
$analysisCases = @(
    @('request','Request manual analysis',350,100), @('parse','Parse supported Java source',780,100),
    @('persist','Atomic full-analysis upsertAnalysis',1210,100), @('status','Publish ANALYZING / ANALYZED / FAILED',780,350),
    @('watch','Watch local project files',350,610), @('delta','Replace one file graph slice',780,610),
    @('broadcast','Broadcast STOMP update',1210,610)
)
foreach ($u in $analysisCases) { [void]$cells.Add((New-UseCase $u[0] $u[1] $u[2] $u[3] 340 100)) }
[void]$cells.Add((New-Edge 'u-request' 'user' 'request')); [void]$cells.Add((New-Edge 'request-parse' 'request' 'parse'))
[void]$cells.Add((New-Edge 'parse-persist' 'parse' 'persist')); [void]$cells.Add((New-Edge 'parse-status' 'parse' 'status'))
[void]$cells.Add((New-Edge 'system-watch' 'system' 'watch')); [void]$cells.Add((New-Edge 'watch-delta' 'watch' 'delta'))
[void]$cells.Add((New-Edge 'delta-broadcast' 'delta' 'broadcast')); [void]$cells.Add((New-Edge 'status-broadcast' 'status' 'broadcast'))
[void]$cells.Add((New-Note 'note' 'Full analysis uses GraphRepository.upsertAnalysis. The watcher uses separate deleteFile/upsertNodes/upsertEdges calls and does not prove one rollback transaction.' 390 880 1080 100))
[void]$useCasePages.Add((New-Page 'uc-analysis' 'Use Case Phân tích mã nguồn (User, System)' $cells))

$cells = [Collections.ArrayList]::new()
[void]$cells.Add((New-Title 'Graph Exploration'))
[void]$cells.Add((New-Actor 'user' 'User' 90 430))
$graphCases = @(
    @('full','Get full graph',380,100), @('filter','Filter and cap graph payload',800,100),
    @('render','Render Sigma.js 2D graph',1220,100), @('neighbors','Inspect neighbors',380,380),
    @('source','Read bounded source slice',800,380), @('meta','Read truncation metadata',1220,380)
)
foreach ($u in $graphCases) { [void]$cells.Add((New-UseCase $u[0] $u[1] $u[2] $u[3] 330 95)) }
foreach ($target in @('full','neighbors','source')) { [void]$cells.Add((New-Edge "u-$target" 'user' $target)) }
[void]$cells.Add((New-Edge 'full-filter' 'full' 'filter')); [void]$cells.Add((New-Edge 'filter-render' 'filter' 'render')); [void]$cells.Add((New-Edge 'filter-meta' 'filter' 'meta'))
[void]$cells.Add((New-Note 'note' 'Evidence: GraphController, GraphResponseFilter, GraphPayloadGuard, SourceController and GraphCanvas.vue. No 3D renderer is claimed.' 350 720 1100 100))
[void]$useCasePages.Add((New-Page 'uc-graph' 'Use Case Xem & Tương tác Graph (User)' $cells))

$cells = [Collections.ArrayList]::new()
[void]$cells.Add((New-Title 'Use-case UML Response'))
[void]$cells.Add((New-Actor 'user' 'User' 90 400))
$umlCases = @(
    @('request','GET /api/projects/{projectId}/diagrams/usecase',350,100),
    @('guard','Check owner, feature and ANALYZED status',800,100),
    @('infer','Infer actors / goals / relations',800,360),
    @('response','Return source/confidence, warnings, PlantUML, Mermaid and views',1200,360),
    @('render','Render sanitized SVG; zoom / fit / fullscreen / PNG',800,650)
)
foreach ($u in $umlCases) { [void]$cells.Add((New-UseCase $u[0] $u[1] $u[2] $u[3] 380 105)) }
[void]$cells.Add((New-Edge 'u-request' 'user' 'request')); Add-EdgeSequence $cells @('request','guard','infer','response','render') 'uml'
[void]$cells.Add((New-Note 'note' 'Only the use-case diagram endpoint is evidenced. The DTO has warnings and element source/confidence fields; it has no generic evidence-notes field.' 410 880 1100 100))
[void]$useCasePages.Add((New-Page 'uc-uml' 'Use Case Xem UML Diagram (User)' $cells))

$cells = [Collections.ArrayList]::new()
[void]$cells.Add((New-Title 'Impact Analysis'))
[void]$cells.Add((New-Actor 'user' 'User' 90 420))
$impactCases = @(
    @('request','GET /graph/impact?nodeId=&depth=&profile=',350,120), @('owner','Assert project ownership',800,120),
    @('repo','Traverse bounded graph impact',800,400), @('result','Return affected nodes / paths / metadata',1200,400)
)
foreach ($u in $impactCases) { [void]$cells.Add((New-UseCase $u[0] $u[1] $u[2] $u[3] 380 105)) }
[void]$cells.Add((New-Edge 'u-request' 'user' 'request')); Add-EdgeSequence $cells @('request','owner','repo','result') 'impact'
[void]$cells.Add((New-Note 'note' 'The REST impact endpoint is distinct from MCP test-plan suggestion tools.' 420 760 1000 90))
[void]$useCasePages.Add((New-Page 'uc-impact' 'Use Case Impact Analysis (User)' $cells))

$cells = [Collections.ArrayList]::new()
[void]$cells.Add((New-Title 'Source Read, MCP Search and Local CLI Patch'))
[void]$cells.Add((New-Actor 'user' 'Browser User' 80 180)); [void]$cells.Add((New-Actor 'ai' 'AI Coding Client' 80 430)); [void]$cells.Add((New-Actor 'cli' 'CLI / Project-bound API Key' 80 700))
$sourceCases = @(
    @('read','Read bounded source slice',380,180), @('search','Use MCP source/search tools',800,180),
    @('patch','POST /api/projects/{id}/patch or /current/patch',380,570),
    @('validate','Validate ownership, paths, content and limits',830,570),
    @('commit','Atomically commit filesystem changes',1250,570),
    @('reanalyze','Schedule coalesced full async re-analysis',830,790)
)
foreach ($u in $sourceCases) { [void]$cells.Add((New-UseCase $u[0] $u[1] $u[2] $u[3] 360 100)) }
[void]$cells.Add((New-Edge 'user-read' 'user' 'read')); [void]$cells.Add((New-Edge 'ai-search' 'ai' 'search'))
[void]$cells.Add((New-Edge 'cli-patch' 'cli' 'patch')); Add-EdgeSequence $cells @('patch','validate','commit','reanalyze') 'patch'
[void]$cells.Add((New-Note 'note' 'LocalPatchController documents this as a CLI/JWT-or-API-key endpoint. No browser frontend caller is asserted.' 390 960 1080 90))
[void]$useCasePages.Add((New-Page 'uc-source' 'Use Case Đọc/Tìm kiếm Source' $cells))

$cells = [Collections.ArrayList]::new()
[void]$cells.Add((New-Title 'Admin and Security Operations'))
[void]$cells.Add((New-Actor 'admin' 'Admin' 70 480))
$adminCases = @(
    @('users','Manage users / account state',330,80), @('plans','Manage plans / feature flags',760,80),
    @('credits','Read / adjust credit balances',1190,80), @('pricing','Create / update / deactivate pricing rules',330,310),
    @('audit','Read / stream audit logs',760,310), @('security','Read / stream security events',1190,310),
    @('abuse','Request events / top users / top IPs / suspicious networks',330,560),
    @('ipblocks','List / create / update / delete IP blocks',760,560), @('support','Announcements / support reports',1190,560),
    @('storage','Read-only storage overview',760,820)
)
foreach ($u in $adminCases) { [void]$cells.Add((New-UseCase $u[0] $u[1] $u[2] $u[3] 360 105)) }
foreach ($target in @('users','plans','credits','pricing','audit','security','abuse','ipblocks','support','storage')) { [void]$cells.Add((New-Edge "a-$target" 'admin' $target)) }
[void]$cells.Add((New-Note 'note' 'Evidence: AdminCreditController, AdminPricingController, AdminAbuseController, AdminAuditController, AdminSecurityMonitorController and AdminStorageController.' 350 1010 1100 90))
[void]$useCasePages.Add((New-Page 'uc-admin' 'Use Case Quản trị hệ thống (Admin)' $cells 1800 1180))

Write-MxFile '1.Usecase Diagram' $useCasePages

# Activity pages preserve the original six page names while correcting execution order.
$activityPages = [Collections.ArrayList]::new()

$cells = [Collections.ArrayList]::new(); [void]$cells.Add((New-Title 'Authenticate Flow - Local, OAuth and Rotating Session'))
$steps = @(
    @('start','Register, login or OAuth callback',80,150), @('choice','Authentication path?',520,140,'decision'),
    @('register','Register: validate and persist new User/settings',830,60), @('login','Login: throttle and authenticate existing User',830,190),
    @('oauth','OAuth handler: link/create verified identity',830,320), @('issue','Issue JWT + hashed refresh-session token',1230,190),
    @('cookies','Controller/handler uses AuthCookieService to set cookies',1230,390), @('refresh','Refresh: lock/rotate row; replace or clear cookies',830,590),
    @('logout','Logout: revoke session; controller clears cookies',1230,590)
)
foreach ($s in $steps) { if ($s.Count -gt 4 -and $s[4] -eq 'decision') { [void]$cells.Add((New-Decision $s[0] $s[1] $s[2] $s[3])) } else { [void]$cells.Add((New-Step $s[0] $s[1] $s[2] $s[3] 340 85)) } }
[void]$cells.Add((New-Edge 'e0' 'start' 'choice')); [void]$cells.Add((New-Edge 'e1' 'choice' 'register' 'register')); [void]$cells.Add((New-Edge 'e2' 'choice' 'login' 'login')); [void]$cells.Add((New-Edge 'e3' 'choice' 'oauth' 'OAuth'))
foreach ($source in @('register','login','oauth')) { [void]$cells.Add((New-Edge "e-$source" $source 'issue')) }
[void]$cells.Add((New-Edge 'e4' 'issue' 'cookies')); [void]$cells.Add((New-Edge 'e5' 'cookies' 'refresh' 'token expires')); [void]$cells.Add((New-Edge 'e6' 'cookies' 'logout' 'logout'))
[void]$cells.Add((New-Note 'note' 'AuthController/AuthService/AuthCookieService/OAuth2LoginSuccessHandler evidence. Login does not unconditionally persist a User.' 350 850 1100 90))
[void]$activityPages.Add((New-Page 'act-auth' 'Authenticate Flow' $cells))

$cells = [Collections.ArrayList]::new(); [void]$cells.Add((New-Title 'Import Project Flow - Synchronous and Asynchronous Branches'))
[void]$cells.Add((New-Step 'source' 'Choose archive / GitHub / local source' 80 180 330 85))
[void]$cells.Add((New-Step 'archive' 'Archive: extract + register project' 500 60 350 85)); [void]$cells.Add((New-Step 'github' 'GitHub: fetch tarball + register project' 500 200 350 85)); [void]$cells.Add((New-Step 'local' 'Local: validate root + register project' 500 340 350 85))
[void]$cells.Add((New-Decision 'syncq' 'Synchronous archive?' 950 80)); [void]$cells.Add((New-Step 'sync' 'Analyze inline and return 200' 1270 40 360 90)); [void]$cells.Add((New-Step 'submit' 'Submit analysisExecutor before service returns' 950 310 400 95)); [void]$cells.Add((New-Step 'accepted' 'Controller returns 202 ANALYZING/progress=0' 1380 310 360 95)); [void]$cells.Add((New-Step 'analyze' 'AnalyzeService parses and atomically upserts full analysis' 950 550 400 95)); [void]$cells.Add((New-Step 'terminal' 'Mark ANALYZED/FAILED and broadcast status' 1380 550 360 95))
foreach ($target in @('archive','github','local')) { [void]$cells.Add((New-Edge "source-$target" 'source' $target)) }
[void]$cells.Add((New-Edge 'archive-q' 'archive' 'syncq')); [void]$cells.Add((New-Edge 'q-sync' 'syncq' 'sync' 'yes/default')); [void]$cells.Add((New-Edge 'q-submit' 'syncq' 'submit' 'async=true'))
[void]$cells.Add((New-Edge 'github-submit' 'github' 'submit')); [void]$cells.Add((New-Edge 'local-submit' 'local' 'submit')); [void]$cells.Add((New-Edge 'submit-accepted' 'submit' 'accepted')); [void]$cells.Add((New-Edge 'submit-analyze' 'submit' 'analyze')); [void]$cells.Add((New-Edge 'analyze-terminal' 'analyze' 'terminal'))
[void]$cells.Add((New-Note 'note' 'The default archive branch returns 200 and does not flow through the async 202 node.' 420 820 1050 85))
[void]$activityPages.Add((New-Page 'act-import' 'Import Project Flow' $cells))

$cells = [Collections.ArrayList]::new(); [void]$cells.Add((New-Title 'Manual Code Analysis Flow'))
$manual = @(
    @('request','POST /api/projects/{id}/analyze',100,170), @('guard','Ownership/status validation',520,170),
    @('scheduler','ProjectAnalysisScheduler coalesces duplicate requests',940,170), @('accepted','Return 202 Accepted',1360,170),
    @('analyze','AnalyzeService parse + infer + upsertAnalysis',520,480), @('progress','Broadcast progress and terminal status',940,480),
    @('watch','Start watcher after successful analysis',1360,480)
)
foreach ($s in $manual) { [void]$cells.Add((New-Step $s[0] $s[1] $s[2] $s[3] 340 90)) }; Add-EdgeSequence $cells @('request','guard','scheduler','accepted') 'manual-top'; [void]$cells.Add((New-Edge 'scheduler-analyze' 'scheduler' 'analyze')); Add-EdgeSequence $cells @('analyze','progress','watch') 'manual-bottom'
[void]$cells.Add((New-Note 'note' 'Import-owned async work does not pass through ProjectAnalysisScheduler; this page is the separate manual-analysis path.' 380 760 1080 90))
[void]$activityPages.Add((New-Page 'act-analysis' 'Code Analysis Flow' $cells))

$cells = [Collections.ArrayList]::new(); [void]$cells.Add((New-Title 'Realtime File Watcher Flow - Exact Replacement Order'))
$watch = @(
    @('event','CREATE / MODIFY / DELETE event',80,160), @('filter','WatchService filter + debounce',450,160),
    @('before','getFileSlice(before)',820,160), @('delete','deleteFile(projectId, path)',1190,160),
    @('exists','CREATE/MODIFY and file exists?',820,390,'decision'), @('parse','parseFile -> upsertNodes -> upsertEdges',1190,390),
    @('after','getFileSlice(after)',820,650), @('delta','Compute added/removed delta',1190,650),
    @('broadcast','Broadcast INCREMENTAL update',1190,850)
)
foreach ($s in $watch) { if ($s.Count -gt 4 -and $s[4] -eq 'decision') { [void]$cells.Add((New-Decision $s[0] $s[1] $s[2] $s[3])) } else { [void]$cells.Add((New-Step $s[0] $s[1] $s[2] $s[3] 330 85)) } }
Add-EdgeSequence $cells @('event','filter','before','delete','exists') 'watch'; [void]$cells.Add((New-Edge 'exists-parse' 'exists' 'parse' 'yes')); [void]$cells.Add((New-Edge 'parse-after' 'parse' 'after')); [void]$cells.Add((New-Edge 'exists-after' 'exists' 'after' 'delete/no file')); Add-EdgeSequence $cells @('after','delta','broadcast') 'watch-end'
[void]$cells.Add((New-Note 'note' 'FileChangeBroadcaster.java:99-123. The three replacement writes are separate calls; no full rollback is claimed.' 300 980 1050 80))
[void]$activityPages.Add((New-Page 'act-watch' 'Realtime File Watcher Flow' $cells 1800 1150))

$cells = [Collections.ArrayList]::new(); [void]$cells.Add((New-Title 'AI Interaction Flow - MCP Protocol'))
$mcp = @(
    @('request','Streamable HTTP request to /mcp',100,170), @('auth','ApiKeyAuthFilter authenticates key/project',500,170),
    @('resolve','Spring AI resolves one of 18 tools',900,170), @('meter','MeteredToolCallback checks feature/ownership/credits',1300,170),
    @('data','Selected tool resolves its bounded data source',700,500), @('result','Return bounded structured result',1120,500)
)
foreach ($s in $mcp) { [void]$cells.Add((New-Step $s[0] $s[1] $s[2] $s[3] 340 90)) }; Add-EdgeSequence $cells @('request','auth','resolve','meter','data','result') 'mcp'
[void]$cells.Add((New-Note 'note' 'A tool may use graph/source/ownership/memory-specific data. The diagram does not claim every tool reads source files or Neo4j directly.' 390 760 1080 100))
[void]$activityPages.Add((New-Page 'act-mcp' 'AI Interaction Flow (MCP Protocol)' $cells))

$cells = [Collections.ArrayList]::new(); [void]$cells.Add((New-Title 'Admin Operations and Event Streams'))
[void]$cells.Add((New-Step 'admin' 'ADMIN-authenticated request' 80 190 330 90)); [void]$cells.Add((New-Decision 'kind' 'Operation kind?' 500 180)); [void]$cells.Add((New-Step 'users' 'User/account state operation' 820 60 350 85)); [void]$cells.Add((New-Step 'security' 'Read abuse/request data or mutate IP block' 820 200 350 100)); [void]$cells.Add((New-Step 'audit' 'Read audit log / retention' 820 360 350 85)); [void]$cells.Add((New-Step 'stream' 'Subscribe audit/security SSE' 820 520 350 85)); [void]$cells.Add((New-Step 'result' 'Return sanitized response or SSE event' 1280 280 370 100))
[void]$cells.Add((New-Edge 'admin-kind' 'admin' 'kind')); foreach ($target in @('users','security','audit','stream')) { [void]$cells.Add((New-Edge "kind-$target" 'kind' $target)) }; foreach ($source in @('users','security','audit','stream')) { [void]$cells.Add((New-Edge "$source-result" $source 'result')) }
[void]$cells.Add((New-Note 'note' 'The page preserves the old User Management slot but does not imply all admin CRUD occurs through SSE.' 400 800 1050 90))
[void]$activityPages.Add((New-Page 'act-admin' 'User Management Flow' $cells))

Write-MxFile '2.Activity Diagram' $activityPages

# Full PostgreSQL and Neo4j ERD pages.
$erdPages = [Collections.ArrayList]::new()
$cells = [Collections.ArrayList]::new(); [void]$cells.Add((New-Title 'PostgreSQL Control-plane ERD - 21 Tables / 23 Foreign Keys' 3300))
$tables = [ordered]@{
    users = @('PK id UUID','email VARCHAR','password_hash VARCHAR?','display_name/avatar_url VARCHAR?','email_verified BOOLEAN','role VARCHAR','quota_bytes/used_bytes BIGINT','deactivated + reasons','created_at/updated_at')
    user_identities = @('PK id UUID','FK user_id','provider + provider_user_id','email?','created_at')
    projects = @('PK project_id','FK owner_id','name/source_type','size_bytes/status','deleted_at?','created_at/updated_at')
    api_keys = @('PK id UUID','FK user_id','FK project_id?','key_hash UNIQUE','key_prefix/name','last_used/expires/disabled/deleted','disabled_by/reason/locked_by','created_at')
    plans = @('PK id UUID','code UNIQUE/name','storage/api-key/credit limits','contact_sales/is_active/sort_order','created_at/updated_at')
    user_account_settings = @('PK/FK user_id','FK plan_id','quota overrides','api_key_creation_disabled','blocked_at/reasons','created_at/updated_at')
    project_usage = @('PK/FK project_id','FK owner_id','storage_bytes','updated_at')
    user_credit_balances = @('PK id UUID','FK user_id','period_start/end','limit snapshot/used/adjustment','created_at/updated_at')
    credit_pricing_rules = @('PK id UUID','operation_code UNIQUE','display_name','credit factors/minimum','is_active','created_at/updated_at')
    credit_ledger = @('PK id UUID','FK user_id','FK project_id?','FK balance_id?','source/operation_code','credits_delta/metadata','created_at')
    feature_flags = @('PK id UUID','flag_key UNIQUE','scope/display_name','description?','enabled','created_at/updated_at')
    announcements = @('PK id UUID','type/severity/target','title/body','starts_at/ends_at','dismissible/active','FK created_by_user_id?','created_at/updated_at')
    user_notifications = @('PK id UUID','FK user_id','FK announcement_id','read_at/dismissed_at','created_at')
    feedback_reports = @('PK id UUID','FK user_id?','status/category/title','delete_after?','created_at/closed_at?')
    feedback_messages = @('PK id UUID','FK report_id','FK sender_user_id?','sender_role/body','created_at')
    security_events = @('PK id UUID','event_type/severity','FK subject_user_id?','api_key_ref/source/description','created_at')
    request_events = @('PK id UUID','FK user_id?','api_key_ref/ip_address','route/http_method/status','event_type/occurred_at')
    ip_blocks = @('PK id UUID','ip_address UNIQUE','safe_reason/expires_at?','FK created_by?','active','created_at/updated_at')
    audit_logs = @('PK id UUID','actor/target UUID logical refs','target_type/target_id','action/outcome/ip_address/details','created_at')
    audit_retention_settings = @('PK id SMALLINT','retention_days','FK updated_by?','updated_at')
    refresh_sessions = @('PK id UUID','FK user_id','family_id/token_hash UNIQUE','expires/last_used/revoked','revoke_reason/replaced_by_id','created_at')
}
$positions = @{}
$index = 0
foreach ($entry in $tables.GetEnumerator()) {
    $column = $index % 4
    $row = [Math]::Floor($index / 4)
    $x = 70 + ($column * 980)
    $y = 110 + ($row * 410)
    $positions[$entry.Key] = @($x, $y)
    $value = $entry.Key + "`n" + ($entry.Value -join "`n")
    [void]$cells.Add((New-Cell $entry.Key $value $x $y 760 330 'swimlane;startSize=34;whiteSpace=wrap;html=1;fillColor=#f8fafc;strokeColor=#334155;fontSize=12;align=left;verticalAlign=top;spacing=7;fontStyle=0;'))
    $index++
}
$fkEdges = @(
    @('users','user_identities','user_id'), @('users','projects','owner_id'), @('users','api_keys','user_id'), @('projects','api_keys','project_id nullable'),
    @('users','user_account_settings','user_id 0..1'), @('plans','user_account_settings','plan_id'), @('projects','project_usage','project_id 0..1'), @('users','project_usage','owner_id'),
    @('users','user_credit_balances','user_id'), @('users','credit_ledger','user_id'), @('projects','credit_ledger','project_id nullable'), @('user_credit_balances','credit_ledger','balance_id nullable'),
    @('users','announcements','created_by nullable'), @('users','user_notifications','user_id'), @('announcements','user_notifications','announcement_id'), @('users','feedback_reports','user_id nullable'),
    @('feedback_reports','feedback_messages','report_id'), @('users','feedback_messages','sender nullable'), @('users','security_events','subject nullable'), @('users','request_events','user_id nullable'),
    @('users','ip_blocks','created_by nullable'), @('users','audit_retention_settings','updated_by nullable'), @('users','refresh_sessions','user_id')
)
$i = 0; foreach ($edge in $fkEdges) { [void]$cells.Add((New-Edge "fk-$i" $edge[0] $edge[1] $edge[2] 'edgeStyle=orthogonalEdgeStyle;rounded=0;html=1;endArrow=ERmany;startArrow=ERone;')); $i++ }
[void]$cells.Add((New-Note 'schema-note' 'Runtime schema audit 2026-08-14T10:12:42+07:00: 21 domain tables, 23 FKs, 66 domain-table indexes (68 public including two Flyway indexes), 19 successful migrations. Unique indexes include uq_users_email_lower, uq_identity_provider_uid, uq_credit_balance_user_period, uq_user_notifications_user_announcement and partial uq_api_keys_live_user_project.' 70 2570 3680 150))
[void]$erdPages.Add((New-Page 'erd-pg' 'ERD PostgreSQL' $cells 4000 2850))

$cells = [Collections.ArrayList]::new(); [void]$cells.Add((New-Title 'Neo4j Current Emission, Migration Schema and Observed Runtime' 3000))
$neoNodes = @(
    @('project',':Project`nid/projectId/fullName/name/path`ncreatedAt?/lastAnalyzedAt?',70,130),
    @('package',':Package`nprojectId/fullName/name',480,130), @('file',':File`nprojectId/filePath/name',890,130),
    @('type',':Class / Interface / Enum / Record / DBModel`nprojectId/fullName/name/springLayer?',1300,130),
    @('method',':Method / Constructor`nprojectId/fullName/name/paramTypes',1710,130),
    @('member',':Field / LocalVariable`nprojectId/fullName/name',2120,130),
    @('annotation',':Annotation`nprojectId/fullName/name',2530,130),
    @('endpoint',':APIEndpoint`nprojectId/httpMethod/routePath`nruntime count 620',2940,130),
    @('route',':Route (schema-only current runtime)`nV1 route_unique + route_path`nruntime count 0',2940,430),
    @('external',':External`nprojectId/fullName/name',2530,430)
)
foreach ($n in $neoNodes) { [void]$cells.Add((New-Step $n[0] $n[1] $n[2] $n[3] 350 150 '#f0fdf4' '#16a34a')) }
$neoEdges = @(
    @('project','package','CONTAINS'), @('package','file','CONTAINS'), @('file','type','DEFINES current emitter'),
    @('type','method','HAS_METHOD'), @('type','member','HAS_FIELD'), @('type','type','HAS_INNER / EXTENDS / IMPLEMENTS / HAS_RELATION / INJECTS'),
    @('method','method','CALLS / RESOLVES_TO / OVERRIDES / STEP_IN_FLOW'), @('method','type','RETURNS / THROWS / PARAMETER_TYPE / INSTANTIATES / CATCHES'),
    @('method','member','READS / WRITES'), @('type','type','IMPORTS'), @('type','external','IMPORTS / INJECTS'), @('type','package','IMPORTS'),
    @('member','type','TYPE_OF'), @('method','endpoint','HANDLES_ROUTE'), @('type','annotation','ANNOTATED_BY legacy persisted')
)
$i=0; foreach($edge in $neoEdges){$style=$script:DefaultEdgeStyle;if($edge[2] -like '*legacy*'){$style='edgeStyle=orthogonalEdgeStyle;dashed=1;html=1;endArrow=open;'};[void]$cells.Add((New-Edge "rel-$i" $edge[0] $edge[1] $edge[2] $style));$i++}
[void]$cells.Add((New-Note 'neo-note' 'Runtime 2026-08-14T10:12:42+07:00: 56,724 nodes / 116,987 relationships. ANNOTATED_BY=1,712 is legacy persisted data. Current DEFINES emits File -> Class/Interface/Enum/Record/DBModel; runtime contains older endpoints. V1 exact constraints/indexes and V2 Symbol indexes are listed in canonical PlantUML.' 270 930 2780 150))
[void]$erdPages.Add((New-Page 'erd-neo' 'ERD Neo4j' $cells 3400 1200))
Write-MxFile '3.ERD Diagram' $erdPages

# Component/deployment page.
$cells = [Collections.ArrayList]::new(); [void]$cells.Add((New-Title 'VibeGraph Component and Docker Deployment' 2300))
[void]$cells.Add((New-Step 'clients' 'Browser / CLI / AI client' 80 350 300 100 '#fff7ed' '#ea580c'))
[void]$cells.Add((New-Step 'fe' 'Frontend container`nnginx:1.27-alpine`nhost 3000 -> 80`nVue 3 + Vite + Sigma.js' 500 100 420 180 '#ecfeff' '#0891b2'))
[void]$cells.Add((New-Step 'be' 'Backend container`nSpring Boot 4.0.6 / Java 21`nREST + Auth + Parser + Graph/Watcher/STOMP + UML + MCP' 1050 100 600 200 '#eff6ff' '#2563eb'))
[void]$cells.Add((New-Step 'repo' 'GraphRepository facade`nNeo4jGraphRepository owns raw Driver' 1130 400 440 130 '#f0fdf4' '#16a34a'))
[void]$cells.Add((New-Step 'pg' 'PostgreSQL 16.11`n127.0.0.1:5433 -> 5432`ncontrol-plane/auth/status' 1850 100 420 150 '#fef2f2' '#dc2626'))
[void]$cells.Add((New-Step 'neo' 'Neo4j 5.26`n127.0.0.1:7474 / 7687`ncode graph' 1850 360 420 150 '#fef2f2' '#dc2626'))
[void]$cells.Add((New-Step 'mounts' 'Writable bind mounts`n./projects -> /app/projects`n./uploads -> /app/uploads' 500 650 500 150 '#fff7ed' '#ea580c'))
[void]$cells.Add((New-Step 'supa' 'Optional realtime/high-volume`nPostgreSQL-compatible storage`ndisabled by default' 1750 690 560 150 '#faf5ff' '#9333ea'))
[void]$cells.Add((New-Edge 'c-fe' 'clients' 'fe' 'HTTP')); [void]$cells.Add((New-Edge 'c-be' 'clients' 'be' 'REST / MCP X-API-Key')); [void]$cells.Add((New-Edge 'fe-be' 'fe' 'be' 'REST JSON + STOMP /ws/graph-updates')); [void]$cells.Add((New-Edge 'be-repo' 'be' 'repo' 'Parser/Graph/MCP via repository/services')); [void]$cells.Add((New-Edge 'repo-neo' 'repo' 'neo' 'raw Driver isolated here')); [void]$cells.Add((New-Edge 'be-pg' 'be' 'pg' 'JDBC / Flyway / ownership/status')); [void]$cells.Add((New-Edge 'mounts-be' 'mounts' 'be' 'writable'))
$healthStyle='edgeStyle=orthogonalEdgeStyle;dashed=1;html=1;endArrow=open;'; [void]$cells.Add((New-Edge 'health-pg' 'be' 'pg' 'depends_on healthy' $healthStyle)); [void]$cells.Add((New-Edge 'health-neo' 'be' 'neo' 'depends_on healthy' $healthStyle)); [void]$cells.Add((New-Edge 'health-fe' 'fe' 'be' 'depends_on healthy' $healthStyle)); [void]$cells.Add((New-Edge 'be-supa' 'be' 'supa' 'optional when enabled' $healthStyle))
[void]$cells.Add((New-Note 'note' 'Evidence: docker-compose.yml, backend/frontend Dockerfiles, application.yaml, WebSocketConfig and MCP module guide. This is the checked local topology, not an external production deployment claim.' 420 950 1450 110))
Write-MxFile '4.1.Component_Deployment Diagram' @((New-Page 'component' 'Page-1' $cells 2500 1200))

# Two compact class pages.
$classPages = [Collections.ArrayList]::new()
$cells = [Collections.ArrayList]::new(); [void]$cells.Add((New-Title 'Auth / Control-plane Compact Class View'))
$authClasses = @(
    @('authController','AuthController`nregister/login/refresh/logout/me',80,120), @('authService','AuthService`nregisterSession/loginSession/oauthLoginSession/refreshSession',520,120),
    @('cookie','AuthCookieService',960,120), @('jwt','JwtService',1340,120), @('refreshService','RefreshSessionService`nissue/rotate/revoke/purgeExpiredSessions',520,380),
    @('user','User (JPA entity)',80,620), @('refresh','RefreshSession (JPA entity)',520,620), @('apiKey','ApiKey (JPA entity)',960,620),
    @('ownership','ProjectOwnership (JPA entity)',1340,620), @('trash','ProjectTrashService',1340,380),
    @('adminUser','AdminUserController',80,880), @('adminService','AdminService',440,880),
    @('adminAudit','AdminAuditController',800,880), @('auditService','AuditService + AuditLogEventStream',1160,880),
    @('adminSecurity','AdminSecurityMonitorController',800,1060), @('securityService','AdminSecurityMonitorService + RequestEventStream',1160,1060)
)
foreach($c in $authClasses){[void]$cells.Add((New-Step $c[0] $c[1] $c[2] $c[3] 330 105 '#f8fafc' '#334155'))}
$deps=@(@('authController','authService'),@('authController','cookie'),@('authService','jwt'),@('authService','refreshService'),@('refreshService','refresh'),@('refreshService','user'),@('trash','ownership'),@('adminUser','adminService'),@('adminAudit','auditService'),@('adminSecurity','securityService'))
$i=0;foreach($d in $deps){[void]$cells.Add((New-Edge "d-$i" $d[0] $d[1]));$i++}
[void]$cells.Add((New-Edge 'logical-api' 'user' 'apiKey' 'logical DB FK via userId' 'edgeStyle=orthogonalEdgeStyle;dashed=1;html=1;endArrow=open;')); [void]$cells.Add((New-Edge 'logical-owner' 'user' 'ownership' 'logical DB FK via ownerId' 'edgeStyle=orthogonalEdgeStyle;dashed=1;html=1;endArrow=open;'))
[void]$classPages.Add((New-Page 'class-auth' 'Auth Module' $cells 1800 1350))

$cells = [Collections.ArrayList]::new(); [void]$cells.Add((New-Title 'Graph / Parser / Diagram / MCP Compact Class View' 1600))
$graphClasses = @(
    @('node','NodeData <<record>>',70,110), @('edge','EdgeData <<record>>',390,110), @('parse','ParseResult <<class>>',710,110),
    @('parserI','ParserService <<interface>>',1050,110), @('parserImpl','ParserServiceImpl',1390,110),
    @('repoI','GraphRepository <<interface>>',70,380), @('neoRepo','Neo4jGraphRepository',410,380), @('cacheRepo','CachingGraphRepository <<Primary decorator>>',750,380),
    @('analyzeI','AnalyzeService <<interface>>',1090,380), @('analyzeImpl','AnalyzeServiceImpl',1430,380),
    @('scheduler','ProjectAnalysisScheduler',70,650), @('watchI','FileWatcherService <<interface>>',410,650), @('watchImpl','FileWatcherServiceImpl',750,650),
    @('broadcaster','FileChangeBroadcaster',1090,650), @('update','GraphUpdateController',1430,650),
    @('filter','GraphResponseFilter',70,900), @('guard','GraphPayloadGuard',410,900), @('controller','GraphController',750,900),
    @('diagram','UseCaseDiagramServiceImpl',1090,900), @('engine','UseCaseInferenceEngine + helpers',1430,900),
    @('mcpConfig','McpServerConfig',410,1130), @('callbackI','ToolCallback <<interface>>',750,1130), @('metered','MeteredToolCallback',1090,1130)
)
foreach($c in $graphClasses){[void]$cells.Add((New-Step $c[0] $c[1] $c[2] $c[3] 300 100 '#f8fafc' '#334155'))}
$relations=@(
    @('parse','node','contains'),@('parse','edge','contains'),@('parserI','parserImpl','implements'),@('repoI','neoRepo','implements'),@('repoI','cacheRepo','implements'),@('cacheRepo','neoRepo','delegates'),
    @('analyzeI','analyzeImpl','implements'),@('analyzeImpl','parserI','uses'),@('analyzeImpl','repoI','uses'),@('scheduler','analyzeI','uses'),@('scheduler','update','broadcasts'),
    @('watchI','watchImpl','implements'),@('broadcaster','watchI','depends on interface'),@('broadcaster','repoI','uses'),@('broadcaster','update','broadcasts'),
    @('update','guard','uses'),@('controller','filter','uses'),@('controller','guard','uses'),@('diagram','engine','uses'),@('mcpConfig','metered','registers'),@('callbackI','metered','implemented by'),@('metered','callbackI','delegates')
)
$i=0;foreach($d in $relations){[void]$cells.Add((New-Edge "r-$i" $d[0] $d[1] $d[2]));$i++}
[void]$cells.Add((New-Note 'note' 'Verified against current source and GitNexus (17,907 symbols / 41,198 edges / 300 flows). Plan, balances, ledger, audit, feature flags, Role, GraphService and ProjectService remain in code but are omitted from this compact slice.' 250 1320 1350 110))
[void]$classPages.Add((New-Page 'class-graph' 'Graph/Parser Module' $cells 1800 1500))
Write-MxFile '4.2.Class Diagram' $classPages

Write-Output 'Generated updated diagrams.net artifacts: 10 use-case pages, 6 activity pages, 2 ERD pages, 1 component page, 2 class pages.'
