import { useTranslation } from 'react-i18next'
import { REPORT_PAGE_IDS, TOGGLEABLE_PAGE_IDS, pageI18nKey } from './reportPages'
import type { ReportPageId } from './reportPages'

interface Props {
  selectedPages: readonly ReportPageId[]
  onToggle: (page: ReportPageId) => void
  onSelectAll: () => void
  onDeselectAll: () => void
}

export function ReportPageSelector({ selectedPages, onToggle, onSelectAll, onDeselectAll }: Props) {
  const { t } = useTranslation()

  return (
    <div className="report-page-selector">
      <fieldset style={{ border: '1px solid var(--border)', borderRadius: 4, padding: '0.75rem' }}>
        <legend style={{ padding: '0 0.3rem', fontSize: '0.9rem', fontWeight: 600 }}>
          {t('report.pageSelectionTitle')}
        </legend>

        {/* Convenience buttons */}
        <div style={{ display: 'flex', gap: '0.5rem', marginBottom: '0.75rem' }}>
          <button
            type="button"
            className="btn outline"
            onClick={onSelectAll}
            style={{ fontSize: '0.85rem', padding: '0.3rem 0.7rem' }}
          >
            {t('report.selectAll')}
          </button>
          <button
            type="button"
            className="btn outline"
            onClick={onDeselectAll}
            style={{ fontSize: '0.85rem', padding: '0.3rem 0.7rem' }}
          >
            {t('report.deselectAll')}
          </button>
        </div>

        {/* Page checkboxes */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.4rem' }}>
          {REPORT_PAGE_IDS.map((id) => {
            const isFixed = id === 'SUMMARY' || id === 'AUSWERTUNG'
            const checked = selectedPages.includes(id)
            return (
              <label
                key={id}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '0.5rem',
                  cursor: isFixed ? 'default' : 'pointer',
                  color: isFixed ? 'var(--text-secondary)' : 'inherit',
                  fontSize: '0.9rem',
                }}
              >
                <input
                  type="checkbox"
                  checked={checked}
                  disabled={isFixed}
                  onChange={() => {
                    if (!isFixed) onToggle(id)
                  }}
                  aria-label={t(pageI18nKey(id))}
                />
                {t(pageI18nKey(id))}
                {isFixed && (
                  <span style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>
                    ({t('report.alwaysIncluded')})
                  </span>
                )}
              </label>
            )
          })}
        </div>
      </fieldset>

      {/* Accessibility: show count of deselected toggleable pages */}
      {TOGGLEABLE_PAGE_IDS.filter(id => !selectedPages.includes(id)).length > 0 && (
        <p style={{ fontSize: '0.8rem', color: 'var(--text-secondary)', margin: '0.3rem 0 0' }}>
          {t('report.pagesDeselected', {
            count: TOGGLEABLE_PAGE_IDS.filter(id => !selectedPages.includes(id)).length,
          })}
        </p>
      )}
    </div>
  )
}
