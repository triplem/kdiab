import { useTranslation } from 'react-i18next';
import { useAuth } from 'react-oidc-context';

export const LanguageSwitcher: React.FC = () => {
  const { i18n } = useTranslation();
  const auth = useAuth();

  const changeLanguage = async (lang: string) => {
    await i18n.changeLanguage(lang);

    if (auth.isAuthenticated && auth.user?.access_token) {
      const authority = auth.settings.authority;
      if (!authority) return;
      try {
        const res = await fetch(`${authority}/account`, {
          headers: { Authorization: `Bearer ${auth.user.access_token}` },
        });
        if (res.ok) {
          const profile = await res.json() as Record<string, unknown>;
          const attributes = (profile.attributes as Record<string, unknown>) ?? {};
          await fetch(`${authority}/account`, {
            method: 'POST',
            headers: {
              Authorization: `Bearer ${auth.user.access_token}`,
              'Content-Type': 'application/json',
            },
            body: JSON.stringify({ ...profile, attributes: { ...attributes, locale: [lang] } }),
          });
          await auth.signinSilent();
        }
      } catch {
        // Keycloak persistence failed — language still changed locally
      }
    }
  };

  const currentLang = i18n.language.split('-')[0];

  return (
    <select
      value={currentLang}
      onChange={e => void changeLanguage(e.target.value)}
      style={{ fontSize: '0.8rem', padding: '0.2rem 0.4rem', borderRadius: '4px', border: '1px solid #ccc', cursor: 'pointer' }}
      aria-label="Select language"
    >
      <option value="en">EN</option>
      <option value="de">DE</option>
    </select>
  );
};
