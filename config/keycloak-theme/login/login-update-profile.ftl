<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('email','firstName','lastName'); section>

    <#if section = "form">
        <form id="kc-update-profile-form" action="${url.loginAction}" method="post" class="kc-form">

            <div class="kc-form-group">
                <label for="email" class="kc-label">${msg("email")}</label>
                <input
                    type="email"
                    id="email"
                    name="email"
                    value="${(user.email!'')}"
                    autofocus
                    autocomplete="email"
                    class="kc-input<#if messagesPerField.existsError('email')> kc-input-error</#if>"
                    aria-invalid="<#if messagesPerField.existsError('email')>true</#if>"
                />
                <#if messagesPerField.existsError('email')>
                    <span class="kc-field-error" aria-live="polite">${kcSanitize(messagesPerField.get('email'))?no_esc}</span>
                </#if>
            </div>

            <div class="kc-form-group">
                <label for="firstName" class="kc-label">${msg("firstName")}</label>
                <input
                    type="text"
                    id="firstName"
                    name="firstName"
                    value="${(user.firstName!'')}"
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
                    value="${(user.lastName!'')}"
                    autocomplete="family-name"
                    class="kc-input<#if messagesPerField.existsError('lastName')> kc-input-error</#if>"
                    aria-invalid="<#if messagesPerField.existsError('lastName')>true</#if>"
                />
                <#if messagesPerField.existsError('lastName')>
                    <span class="kc-field-error" aria-live="polite">${kcSanitize(messagesPerField.get('lastName'))?no_esc}</span>
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
