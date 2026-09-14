param(
    [string]$BaseUrl = 'http://127.0.0.1:8082/api/v1'
)

$ErrorActionPreference = 'Stop'
$results = [System.Collections.Generic.List[object]]::new()

function Invoke-Api {
    param(
        [string]$Name,
        [string]$Method,
        [string]$Path,
        [int[]]$ExpectedStatus,
        [string]$Token,
        [object]$Body,
        [scriptblock]$Assert,
        [string]$Classification = 'implemented'
    )

    $headers = @{ Accept = 'application/json' }
    if ($Token) { $headers.Authorization = "Bearer $Token" }
    $request = @{
        Uri = "$BaseUrl$Path"
        Method = $Method
        Headers = $headers
        SkipHttpErrorCheck = $true
        TimeoutSec = 20
    }
    if ($null -ne $Body) {
        $request.ContentType = 'application/json'
        $request.Body = $Body | ConvertTo-Json -Depth 10 -Compress
    }

    $watch = [System.Diagnostics.Stopwatch]::StartNew()
    $response = Invoke-WebRequest @request
    $watch.Stop()
    $json = if ([string]::IsNullOrWhiteSpace($response.Content)) {
        $null
    } else {
        try { $response.Content | ConvertFrom-Json -Depth 30 } catch { $response.Content }
    }

    $statusMatched = $ExpectedStatus -contains [int]$response.StatusCode
    $assertionPassed = if ($Assert) { [bool](& $Assert $json) } else { $true }
    $passed = $statusMatched -and $assertionPassed

    $results.Add([ordered]@{
        name = $Name
        classification = $Classification
        request = [ordered]@{ method = $Method; path = $Path; body = $Body }
        expectedStatus = $ExpectedStatus
        actualStatus = [int]$response.StatusCode
        elapsedMs = $watch.ElapsedMilliseconds
        passed = $passed
        response = $json
    })

}

function Login {
    param([string]$Email)
    $body = @{ email = $Email; password = 'Demo1234!' }
    $response = Invoke-WebRequest -Uri "$BaseUrl/auth/login" -Method Post `
        -ContentType 'application/json' -Body ($body | ConvertTo-Json -Compress) `
        -SkipHttpErrorCheck -TimeoutSec 20
    if ($response.StatusCode -ne 200) {
        throw "Login failed for ${Email}: HTTP $($response.StatusCode) $($response.Content)"
    }
    return ($response.Content | ConvertFrom-Json).data.accessToken
}

$sellerToken = Login 'kim.fixture.seller@gongguhan.test'
$buyerToken = Login 'kim.fixture.buyer@gongguhan.test'
$buyer2Token = Login 'kim.fixture.buyer2@gongguhan.test'

Invoke-Api 'CAT-01 카테고리 20개' GET '/categories' @(200) $null $null {
    param($json) $json.success -and @($json.data).Count -eq 20 -and $json.data[0].categoryId -eq '10001'
}

Invoke-Api 'PRD-02 판매자 상품 4개' GET '/sellers/products?page=0&size=20' @(200) $sellerToken $null {
    param($json) $json.success -and @($json.data | Where-Object { [long]$_.productId -ge 920001 -and [long]$_.productId -le 920004 }).Count -eq 4
}

Invoke-Api 'PRD 권한 분리' GET '/sellers/products?page=0&size=20' @(403) $buyerToken $null {
    param($json) -not $json.success -and $json.error.code -eq 'ROLE_ACCESS_DENIED'
}

Invoke-Api 'PRD-03 상품 상세' GET '/sellers/products/920001' @(200) $sellerToken $null {
    param($json) $json.success -and [string]$json.data.productId -eq '920001'
}

Invoke-Api 'WDR-01 제한 사유' GET '/withdrawal-restriction-reasons' @(200) $sellerToken $null {
    param($json) $json.success -and @($json.data).Count -ge 5
}

Invoke-Api 'GRP-01 공개 모집 목록' GET '/group-buys?status=RECRUITING&sort=popular&page=0&size=20' @(200) $null $null {
    param($json)
    $ids = @($json.data | ForEach-Object { [string]$_.id })
    $json.success -and $ids -contains '930001' -and $ids -contains '930002'
}

Invoke-Api 'GRP-02 공개 상세' GET '/group-buys/930001' @(200) $null $null {
    param($json) $json.success -and [string]$json.data.id -eq '930001' -and $json.data.status -eq 'RECRUITING'
}

Invoke-Api 'GRP-03 금액 미리보기' POST '/sellers/group-buys/preview' @(200) $sellerToken @{
    productId = 920001; productPrice = 100000; targetCount = 4; shippingFee = 3000
} {
    param($json)
    $json.success -and $json.data.unitShareAmount -eq 25000 -and
        $json.data.estimatedPlatformFee -eq 5000 -and
        $json.data.estimatedSettlementAmount -eq 107000
}

Invoke-Api 'GRP-05 판매자 공구 목록' GET '/sellers/group-buys?page=0&size=20' @(200) $sellerToken $null {
    param($json) $json.success -and @($json.data | Where-Object { [long]$_.id -ge 930001 -and [long]$_.id -le 930003 }).Count -eq 3
}

Invoke-Api 'GRP-06 판매자 운영 상세' GET '/sellers/group-buys/930003' @(200) $sellerToken $null {
    param($json) $json.success -and [string]$json.data.id -eq '930003' -and $json.data.paidCount -eq 2
}

Invoke-Api 'GRP-09 참여자 2명' GET '/sellers/group-buys/930003/participants?page=0&size=20' @(200) $sellerToken $null {
    param($json) $json.success -and @($json.data).Count -eq 2
}

Invoke-Api 'GRP-10 판매자 귀책 철회 미구현 확인' POST '/sellers/group-buys/930003/withdrawal' @(404, 405) $sellerToken @{
    reason = 'fixture 누락 API 확인'
} {
    param($json) $true
} 'known-gap'

Invoke-Api 'RTN-01 신규 반품 미리보기' POST '/users/me/participations/940002/returns/preview' @(200) $buyer2Token @{
    reasonType = 'DEFECT'; detailReason = 'fixture 기능 점검'
} {
    param($json) $json.success -and $json.data.refundAmount -eq 63000 -and $json.data.shippingFeeBearer -eq 'SELLER'
}

Invoke-Api 'RTN-03 구매자 반품 목록' GET '/users/me/returns?page=0&size=20' @(200) $buyerToken $null {
    param($json) $json.success -and @($json.data | Where-Object { [string]$_.id -eq '960001' }).Count -eq 1
}

Invoke-Api 'RTN-04 구매자 반품 상세' GET '/users/me/returns/960001' @(200) $buyerToken $null {
    param($json) $json.success -and [string]$json.data.id -eq '960001' -and $json.data.status -eq 'REQUESTED'
}

Invoke-Api 'RTN-05 판매자 반품 목록' GET '/sellers/returns?page=0&size=20' @(200) $sellerToken $null {
    param($json) $json.success -and @($json.data | Where-Object { [string]$_.id -eq '960001' }).Count -eq 1
}

$summary = [ordered]@{
    generatedAt = [DateTimeOffset]::Now.ToString('o')
    baseUrl = $BaseUrl
    total = $results.Count
    passed = @($results | Where-Object passed).Count
    failed = @($results | Where-Object { -not $_.passed }).Count
    knownGaps = @($results | Where-Object classification -eq 'known-gap').Count
    results = $results
}

$summary | ConvertTo-Json -Depth 30
if ($summary.failed -gt 0) { exit 1 }
