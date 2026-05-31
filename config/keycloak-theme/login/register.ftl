<#import "template.ftl" as layout>
<@layout.registrationLayout
    displayMessage=!messagesPerField.existsError('firstName','lastName','email','username','password','password-confirm'); section>

    <#if section = "form">
        <form id="kc-register-form" action="${url.registrationAction}" method="post" class="kc-form">

            <div class="kc-form-group">
                <label for="firstName" class="kc-label">${msg("firstName")}</label>
                <input
                    type="text"
                    id="firstName"
                    name="firstName"
                    value="${(register.formData.firstName!'')}"
                    autofocus
                    autocomplete="given-name"
                    class="kc-input<#if messagesPerField.existsError('firstName')> kc-input-error</#if>"
                    aria-invalid="<#if messagesPerField.existsError('firstName')>true</#if>"
                />
                <#if messagesPerField.existsError('firstName')>
                    <span class="kc-field-error" aria-live="polite">${kcSanitize(messagesPerField.get('firstName'))?no_esc}</span>
                </#if>
            </div>

            <div class="kc-form-group">
                <label for="lastName" class="kc-label">${msg("lastName")}</label>
                <input
                    type="text"
                    id="lastName"
                    name="lastName"
                    value="${(register.formData.lastName!'')}"
                    autocomplete="family-name"
                    class="kc-input<#if messagesPerField.existsError('lastName')> kc-input-error</#if>"
                    aria-invalid="<#if messagesPerField.existsError('lastName')>true</#if>"
                />
                <#if messagesPerField.existsError('lastName')>
                    <span class="kc-field-error" aria-live="polite">${kcSanitize(messagesPerField.get('lastName'))?no_esc}</span>
                </#if>
            </div>

            <div class="kc-form-group">
                <label for="email" class="kc-label">${msg("email")}</label>
                <input
                    type="email"
                    id="email"
                    name="email"
                    value="${(register.formData.email!'')}"
                    autocomplete="email"
                    class="kc-input<#if messagesPerField.existsError('email')> kc-input-error</#if>"
                    aria-invalid="<#if messagesPerField.existsError('email')>true</#if>"
                />
                <#if messagesPerField.existsError('email')>
                    <span class="kc-field-error" aria-live="polite">${kcSanitize(messagesPerField.get('email'))?no_esc}</span>
                </#if>
            </div>

            <#if !realm.registrationEmailAsUsername>
                <div class="kc-form-group">
                    <label for="username" class="kc-label">${msg("username")}</label>
                    <input
                        type="text"
                        id="username"
                        name="username"
                        value="${(register.formData.username!'')}"
                        autocomplete="username"
                        class="kc-input<#if messagesPerField.existsError('username')> kc-input-error</#if>"
                        aria-invalid="<#if messagesPerField.existsError('username')>true</#if>"
                    />
                    <#if messagesPerField.existsError('username')>
                        <span class="kc-field-error" aria-live="polite">${kcSanitize(messagesPerField.get('username'))?no_esc}</span>
                    </#if>
                </div>
            </#if>

            <#if passwordRequired??>
                <div class="kc-form-group">
                    <label for="password" class="kc-label">${msg("password")}</label>
                    <input
                        type="password"
                        id="password"
                        name="password"
                        autocomplete="new-password"
                        class="kc-input<#if messagesPerField.existsError('password','password-confirm')> kc-input-error</#if>"
                        aria-invalid="<#if messagesPerField.existsError('password','password-confirm')>true</#if>"
                    />
                    <#if messagesPerField.existsError('password')>
                        <span class="kc-field-error" aria-live="polite">${kcSanitize(messagesPerField.get('password'))?no_esc}</span>
                    </#if>
                </div>

                <div class="kc-form-group">
                    <label for="password-confirm" class="kc-label">${msg("passwordConfirm")}</label>
                    <input
                        type="password"
                        id="password-confirm"
                        name="password-confirm"
                        autocomplete="new-password"
                        class="kc-input<#if messagesPerField.existsError('password-confirm')> kc-input-error</#if>"
                        aria-invalid="<#if messagesPerField.existsError('password-confirm')>true</#if>"
                    />
                    <#if messagesPerField.existsError('password-confirm')>
                        <span class="kc-field-error" aria-live="polite">${kcSanitize(messagesPerField.get('password-confirm'))?no_esc}</span>
                    </#if>
                </div>
            </#if>

            <button type="submit" class="kc-submit-btn">
                ${msg("doRegister")}
            </button>
        </form>

    <#elseif section = "info">
        <div class="kc-register">
            <span>${msg("alreadyHaveAccount")}
                <a href="${url.loginUrl}">${msg("doLogIn")}</a>
            </span>
        </div>
    </#if>

</@layout.registrationLayout>
