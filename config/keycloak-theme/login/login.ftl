<#import "template.ftl" as layout>
<@layout.registrationLayout
    displayMessage=!messagesPerField.existsError('username','password')
    displayInfo=realm.password && realm.registrationAllowed && !registrationDisabled??; section>

    <#if section = "form">
        <form id="kc-form-login" action="${url.loginAction}" method="post" class="kc-form">

            <#if !usernameHidden??>
                <div class="kc-form-group">
                    <label for="username" class="kc-label">
                        <#if !realm.loginWithEmailAllowed>
                            ${msg("username")}
                        <#elseif !realm.registrationEmailAsUsername>
                            ${msg("usernameOrEmail")}
                        <#else>
                            ${msg("email")}
                        </#if>
                    </label>
                    <input
                        tabindex="1"
                        id="username"
                        name="username"
                        type="text"
                        value="${(login.username!'')}"
                        autofocus
                        autocomplete="off"
                        class="kc-input<#if messagesPerField.existsError('username','password')> kc-input-error</#if>"
                        aria-invalid="<#if messagesPerField.existsError('username','password')>true</#if>"
                    />
                    <#if messagesPerField.existsError('username','password')>
                        <span class="kc-error-text" aria-live="polite">
                            ${kcSanitize(messagesPerField.getFirstError('username','password'))?no_esc}
                        </span>
                    </#if>
                </div>
            </#if>

            <div class="kc-form-group">
                <label for="password" class="kc-label">${msg("password")}</label>
                <input
                    tabindex="2"
                    id="password"
                    name="password"
                    type="password"
                    autocomplete="current-password"
                    class="kc-input<#if messagesPerField.existsError('username','password')> kc-input-error</#if>"
                    aria-invalid="<#if messagesPerField.existsError('username','password')>true</#if>"
                />
            </div>

            <div class="kc-form-options">
                <#if realm.rememberMe && !usernameHidden??>
                    <label class="kc-checkbox-label">
                        <input
                            tabindex="3"
                            id="rememberMe"
                            name="rememberMe"
                            type="checkbox"
                            <#if login.rememberMe??>checked</#if>
                        >
                        <span>${msg("rememberMe")}</span>
                    </label>
                </#if>
                <#if realm.resetPasswordAllowed>
                    <a tabindex="5" href="${url.loginResetCredentialsUrl}" class="kc-forgot-link">
                        ${msg("doForgotPassword")}
                    </a>
                </#if>
            </div>

            <input type="hidden" name="credentialId"
                <#if auth.selectedCredential?has_content>value="${auth.selectedCredential}"</#if>
            />

            <button tabindex="4" name="login" id="kc-login" type="submit" class="kc-submit-btn">
                ${msg("doLogIn")}
            </button>
        </form>

    <#elseif section = "info">
        <#if realm.password && realm.registrationAllowed && !registrationDisabled??>
            <div class="kc-register">
                <span>${msg("noAccount")}
                    <a tabindex="6" href="${url.registrationUrl}">${msg("doRegister")}</a>
                </span>
            </div>
        </#if>
    </#if>

</@layout.registrationLayout>
