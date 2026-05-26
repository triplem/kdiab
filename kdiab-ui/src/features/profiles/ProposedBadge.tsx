interface ProposedBadgeProps {
  count: number
}

export function ProposedBadge({ count }: ProposedBadgeProps) {
  if (count === 0) return null
  return (
    <span
      className="proposed-badge"
      aria-label={`${count} proposed profiles awaiting decision`}
    >
      {count}
    </span>
  )
}
