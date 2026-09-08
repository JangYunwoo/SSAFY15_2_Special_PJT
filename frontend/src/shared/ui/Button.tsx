import type { ComponentPropsWithoutRef } from 'react'
import { Link, type LinkProps } from 'react-router'

import { cx } from '@/shared/lib/cx'

type Variant = 'solid' | 'ghost'

interface Shared {
  variant?: Variant | undefined
  /** 가로를 꽉 채웁니다 (.btn-block) */
  block?: boolean | undefined
}

const classFor = ({ variant = 'solid', block }: Shared, className?: string): string =>
  cx(variant === 'solid' ? 'btn' : 'btn-ghost', block && 'btn-block', className)

export type ButtonProps = Shared & ComponentPropsWithoutRef<'button'>

export function Button({ variant, block, className, type = 'button', ...rest }: ButtonProps) {
  return <button type={type} className={classFor({ variant, block }, className)} {...rest} />
}

export type ButtonLinkProps = Shared & LinkProps

/** 이동이 목적인 버튼. 시각적으로는 Button 과 같습니다. */
export function ButtonLink({ variant, block, className, ...rest }: ButtonLinkProps) {
  return <Link className={classFor({ variant, block }, className)} {...rest} />
}
