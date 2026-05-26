interface Props {
  patientName: string
  from: string
  to: string
}

function formatDateDE(isoDate: string): string {
  const datePart = isoDate.slice(0, 10)
  const [year, month, day] = datePart.split('-')
  return `${day}.${month}.${year}`
}

export function PrintHeader({ patientName, from, to }: Props) {
  return (
    <div className="print-header" aria-hidden="true">
      <h2>kdiab Analytics Report</h2>
      <p>{patientName} · {formatDateDE(from)} – {formatDateDE(to)}</p>
      <p>Generated: {new Date().toLocaleDateString('de-DE')}</p>
    </div>
  )
}
