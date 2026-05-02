<#macro registrationLayout bodyClass="" displayInfo=false displayMessage=true displayRequiredFields=false>
<!DOCTYPE html>
<html lang="${locale.currentLanguageTag}">
<head>
    <meta charset="utf-8">
    <meta name="robots" content="noindex, nofollow">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>${msg("loginTitle", realm.name)}</title>
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap" rel="stylesheet">
    <link href="${url.resourcesPath}/css/login.css" rel="stylesheet">
</head>
<body>
    <div class="kc-page">
        <div class="kc-card">
            <div class="kc-header">
                <h1 class="kc-title">kdiab</h1>
                <p class="kc-subtitle">${realm.displayName!realm.name}</p>
            </div>

            <#if displayMessage && message?has_content && (message.type != 'warning' || !isAppInitiatedAction??)>
                <div class="kc-alert kc-alert-${message.type}" aria-live="polite">
                    <#if message.type = 'success'><span class="kc-alert-icon">✓</span></#if>
                    <#if message.type = 'warning'><span class="kc-alert-icon">⚠</span></#if>
                    <#if message.type = 'error'><span class="kc-alert-icon">✕</span></#if>
                    <#if message.type = 'info'><span class="kc-alert-icon">ℹ</span></#if>
                    <span>${kcSanitize(message.summary)?no_esc}</span>
                </div>
            </#if>

            <#nested "form">

            <#if displayInfo>
                <div class="kc-info">
                    <#nested "info">
                </div>
            </#if>
        </div>
    </div>
</body>
</html>
</#macro>
