param(
    [string]$SourceMarkdown = "C:\Users\32020\Desktop\occupational _competencies_platform\系统架构与详细设计说明书-职业能力平台.md",
    [string]$OutputDocx = "C:\Users\32020\Desktop\occupational _competencies_platform\系统架构与详细设计说明书-职业能力平台.docx"
)

$ErrorActionPreference = "Stop"

function Add-Paragraph {
    param(
        [Parameter(Mandatory = $true)]$Selection,
        [Parameter(Mandatory = $true)][string]$Text,
        [string]$Style = "Normal",
        [int]$Alignment = 0,
        [int]$FontSize = 12,
        [string]$FontName = "宋体",
        [int]$Bold = 0,
        [double]$LineSpacing = 18
    )

    $Selection.Style = $Style
    $Selection.ParagraphFormat.Alignment = $Alignment
    $Selection.Font.NameFarEast = $FontName
    $Selection.Font.Name = $FontName
    $Selection.Font.Size = $FontSize
    $Selection.Font.Bold = $Bold
    $Selection.ParagraphFormat.LineSpacingRule = 4
    $Selection.ParagraphFormat.LineSpacing = $LineSpacing
    $Selection.TypeText($Text)
    $Selection.TypeParagraph()
}

function Add-BlankLines {
    param(
        [Parameter(Mandatory = $true)]$Selection,
        [int]$Count = 1
    )

    for ($i = 0; $i -lt $Count; $i++) {
        $Selection.TypeParagraph()
    }
}

function Add-MarkdownTable {
    param(
        [Parameter(Mandatory = $true)]$Selection,
        [Parameter(Mandatory = $true)][string[]]$Lines
    )

    if ($Lines.Count -lt 2) {
        return
    }

    $rows = @()
    foreach ($line in $Lines) {
        if (-not $line.Trim().StartsWith("|")) {
            continue
        }
        $cells = $line.Trim().Trim("|").Split("|") | ForEach-Object { $_.Trim() }
        $rows += ,$cells
    }

    if ($rows.Count -lt 2) {
        return
    }

    $header = $rows[0]
    $body = @()
    for ($i = 2; $i -lt $rows.Count; $i++) {
        $body += ,$rows[$i]
    }

    $rowCount = 1 + $body.Count
    $colCount = $header.Count
    $range = $Selection.Range
    $table = $Selection.Tables.Add($range, $rowCount, $colCount)
    $table.Borders.Enable = 1
    $table.Range.Font.NameFarEast = "宋体"
    $table.Range.Font.Name = "宋体"
    $table.Range.Font.Size = 10.5

    for ($c = 1; $c -le $colCount; $c++) {
        $table.Cell(1, $c).Range.Text = $header[$c - 1]
        $table.Cell(1, $c).Range.Bold = 1
        $table.Cell(1, $c).Range.ParagraphFormat.Alignment = 1
    }

    for ($r = 0; $r -lt $body.Count; $r++) {
        for ($c = 1; $c -le $colCount; $c++) {
            $value = ""
            if ($c -le $body[$r].Count) {
                $value = $body[$r][$c - 1]
            }
            $table.Cell($r + 2, $c).Range.Text = $value
        }
    }

    $Selection.MoveDown()
    $Selection.TypeParagraph()
}

function Clean-MarkdownText {
    param([string]$Text)

    $clean = $Text
    $clean = $clean -replace '`', ''
    $clean = $clean -replace '\*\*(.+?)\*\*', '$1'
    $clean = $clean -replace '\*(.+?)\*', '$1'
    return $clean.Trim()
}

if (-not (Test-Path $SourceMarkdown)) {
    throw "Source markdown not found: $SourceMarkdown"
}

$word = $null
$doc = $null

try {
    $word = New-Object -ComObject Word.Application
    $word.Visible = $false
    $doc = $word.Documents.Add()
    $selection = $word.Selection

    $doc.Styles.Item("Normal").Font.NameFarEast = "宋体"
    $doc.Styles.Item("Normal").Font.Name = "宋体"
    $doc.Styles.Item("Normal").Font.Size = 12

    $doc.Styles.Item("Heading 1").Font.NameFarEast = "黑体"
    $doc.Styles.Item("Heading 1").Font.Name = "黑体"
    $doc.Styles.Item("Heading 1").Font.Size = 16
    $doc.Styles.Item("Heading 1").Font.Bold = 1

    $doc.Styles.Item("Heading 2").Font.NameFarEast = "黑体"
    $doc.Styles.Item("Heading 2").Font.Name = "黑体"
    $doc.Styles.Item("Heading 2").Font.Size = 14
    $doc.Styles.Item("Heading 2").Font.Bold = 1

    $doc.Styles.Item("Heading 3").Font.NameFarEast = "黑体"
    $doc.Styles.Item("Heading 3").Font.Name = "黑体"
    $doc.Styles.Item("Heading 3").Font.Size = 12
    $doc.Styles.Item("Heading 3").Font.Bold = 1

    Add-Paragraph -Selection $selection -Text "卷    号" -Alignment 1 -FontSize 12
    Add-Paragraph -Selection $selection -Text "卷内编号" -Alignment 1 -FontSize 12
    Add-Paragraph -Selection $selection -Text "密    级" -Alignment 1 -FontSize 12
    Add-BlankLines -Selection $selection -Count 4
    Add-Paragraph -Selection $selection -Text "项目编号：OCP20260415SD001" -Alignment 1 -FontSize 12
    Add-BlankLines -Selection $selection -Count 2
    Add-Paragraph -Selection $selection -Text "职业能力大数据服务平台" -Alignment 1 -FontSize 22 -FontName "黑体" -Bold 1 -LineSpacing 24
    Add-BlankLines -Selection $selection -Count 1
    Add-Paragraph -Selection $selection -Text "系统架构与详细设计说明书" -Alignment 1 -FontSize 20 -FontName "黑体" -Bold 1 -LineSpacing 24
    Add-Paragraph -Selection $selection -Text "Version: 1.0" -Alignment 1 -FontSize 12
    Add-BlankLines -Selection $selection -Count 3
    Add-Paragraph -Selection $selection -Text "项目承担部门：职业能力平台项目组" -Alignment 1 -FontSize 12
    Add-Paragraph -Selection $selection -Text "撰 写 人：Codex / 项目组" -Alignment 1 -FontSize 12
    Add-Paragraph -Selection $selection -Text "完成日期：2026-04-15" -Alignment 1 -FontSize 12
    Add-Paragraph -Selection $selection -Text "本文档使用部门：项目组 / 前端开发 / 后端开发 / 测试 / 运维" -Alignment 1 -FontSize 12
    Add-Paragraph -Selection $selection -Text "评审负责人：项目负责人" -Alignment 1 -FontSize 12
    Add-Paragraph -Selection $selection -Text "评审日期：2026-04-15" -Alignment 1 -FontSize 12

    $selection.InsertBreak(7)

    Add-Paragraph -Selection $selection -Text "文档信息" -Style "Heading 1"
    Add-Paragraph -Selection $selection -Text "标题：职业能力大数据服务平台"
    Add-Paragraph -Selection $selection -Text "作者：项目组"
    Add-Paragraph -Selection $selection -Text "创建日期：2026-04-15"
    Add-Paragraph -Selection $selection -Text "上次更新日期：2026-04-15"
    Add-Paragraph -Selection $selection -Text "版本：Version 1.0"
    Add-Paragraph -Selection $selection -Text "部门名称：职业能力平台项目组"
    Add-BlankLines -Selection $selection -Count 1

    Add-Paragraph -Selection $selection -Text "修订文档历史记录" -Style "Heading 1"
    $historyRange = $selection.Range
    $historyTable = $selection.Tables.Add($historyRange, 2, 4)
    $historyTable.Borders.Enable = 1
    $historyTable.Range.Font.NameFarEast = "宋体"
    $historyTable.Range.Font.Name = "宋体"
    $historyTable.Range.Font.Size = 10.5
    $historyTable.Cell(1,1).Range.Text = "日期"
    $historyTable.Cell(1,2).Range.Text = "版本"
    $historyTable.Cell(1,3).Range.Text = "说明"
    $historyTable.Cell(1,4).Range.Text = "作者"
    $historyTable.Cell(2,1).Range.Text = "2026-04-15"
    $historyTable.Cell(2,2).Range.Text = "V1.0"
    $historyTable.Cell(2,3).Range.Text = "基于当前项目实现重写系统架构与详细设计说明书"
    $historyTable.Cell(2,4).Range.Text = "项目组"
    $selection.MoveDown()
    $selection.TypeParagraph()
    $selection.InsertBreak(7)

    Add-Paragraph -Selection $selection -Text "目 录" -Alignment 1 -FontSize 16 -FontName "黑体" -Bold 1
    $tocRange = $selection.Range
    $doc.TablesOfContents.Add($tocRange, $true, 1, 3)
    $selection.TypeParagraph()
    $selection.InsertBreak(7)

    $lines = Get-Content -Path $SourceMarkdown -Encoding UTF8
    $tableBuffer = New-Object System.Collections.Generic.List[string]
    $inCodeBlock = $false

    foreach ($line in $lines) {
        $trimmed = $line.TrimEnd()

        if ($trimmed -match '^```') {
            $inCodeBlock = -not $inCodeBlock
            continue
        }

        if ($inCodeBlock) {
            Add-Paragraph -Selection $selection -Text $trimmed -FontName "Consolas" -FontSize 10 -LineSpacing 14
            continue
        }

        if ($trimmed.StartsWith("|")) {
            $tableBuffer.Add($trimmed)
            continue
        }

        if ($tableBuffer.Count -gt 0) {
            Add-MarkdownTable -Selection $selection -Lines $tableBuffer.ToArray()
            $tableBuffer.Clear()
        }

        if ([string]::IsNullOrWhiteSpace($trimmed)) {
            Add-BlankLines -Selection $selection -Count 1
            continue
        }

        if ($trimmed.StartsWith("# ")) {
            Add-Paragraph -Selection $selection -Text (Clean-MarkdownText ($trimmed.Substring(2))) -Style "Heading 1" -Alignment 0 -FontSize 16 -FontName "黑体" -Bold 1
            continue
        }

        if ($trimmed.StartsWith("## ")) {
            Add-Paragraph -Selection $selection -Text (Clean-MarkdownText ($trimmed.Substring(3))) -Style "Heading 2" -Alignment 0 -FontSize 14 -FontName "黑体" -Bold 1
            continue
        }

        if ($trimmed.StartsWith("### ")) {
            Add-Paragraph -Selection $selection -Text (Clean-MarkdownText ($trimmed.Substring(4))) -Style "Heading 3" -Alignment 0 -FontSize 12 -FontName "黑体" -Bold 1
            continue
        }

        if ($trimmed.StartsWith("- ")) {
            Add-Paragraph -Selection $selection -Text ("• " + (Clean-MarkdownText ($trimmed.Substring(2)))) -Style "Normal"
            continue
        }

        if ($trimmed -match '^\d+\.\s+') {
            Add-Paragraph -Selection $selection -Text (Clean-MarkdownText $trimmed) -Style "Normal"
            continue
        }

        Add-Paragraph -Selection $selection -Text (Clean-MarkdownText $trimmed) -Style "Normal"
    }

    if ($tableBuffer.Count -gt 0) {
        Add-MarkdownTable -Selection $selection -Lines $tableBuffer.ToArray()
        $tableBuffer.Clear()
    }

    foreach ($toc in $doc.TablesOfContents) {
        $toc.Update()
    }

    $wdFormatDocumentDefault = 16
    $doc.SaveAs([ref]$OutputDocx, [ref]$wdFormatDocumentDefault)
    $doc.Close()
    $word.Quit()

    Write-Output "Generated: $OutputDocx"
}
catch {
    if ($doc -ne $null) {
        try { $doc.Close($false) } catch {}
    }
    if ($word -ne $null) {
        try { $word.Quit() } catch {}
    }
    throw
}
