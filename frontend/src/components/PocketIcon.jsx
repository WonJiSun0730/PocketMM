export default function PocketIcon({ size = 18, strokeWidth = 1.8, ...props }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={strokeWidth}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      {...props}
    >
      <path d="M5 5.5h14l-1.1 12.1A2.7 2.7 0 0 1 15.2 20H8.8a2.7 2.7 0 0 1-2.7-2.4L5 5.5Z" />
      <path d="M7.5 9.5c1.2 1.1 2.7 1.7 4.5 1.7s3.3-.6 4.5-1.7" />
      <path d="M8 5.5V4h8v1.5" />
    </svg>
  )
}
