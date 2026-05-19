<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('password','password-confirm'); section>

    <#if section = "form">
        <form id="kc-passwd-update-form" action="${url.loginAction}" method="post" class="kc-form">
            <input type="text" id="username" name="username" value="${username}" autocomplete="username"
                   readonly style="display:none;" />

            <div class="kc-form-group">
                <label for="password-new" class="kc-label">${msg("passwordNew")}</label>
                <input
                    type="password"
                    id="password-new"
                    name="password-new"
                    autofocus
                    autocomplete="new-password"
                    class="kc-input<#if messagesPerField.existsError('password','password-confirm')> kc-input-error</#if>"
                    aria-invalid="<#if messagesPerField.existsError('password')>true</#if>"
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

            <div class="kc-form-group" style="margin-top: 1.5rem;">
                <#if isAppInitiatedAction??>
                    <input
                        type="submit"
                        value="${msg("doSubmit")}"
                        class="kc-submit-btn"
                        style="margin-bottom: 0.75rem;"
                    />
                    <button
                        type="submit"
                        name="cancel-aia"
                        value="true"
                        class="kc-submit-btn"
                        style="background: transparent; border: 1px solid var(--accent-primary); color: var(--accent-primary);"
                    >
                        ${msg("doCancel")}
                    </button>
                <#else>
                    <input
                        type="submit"
                        value="${msg("doSubmit")}"
                        class="kc-submit-btn"
                    />
                </#if>
            </div>
        </form>
    </#if>

</@layout.registrationLayout>
