import type { ReactNode } from 'react'
import { Box, Stack, Typography } from '@mui/material'
import { useTranslation } from 'react-i18next'

interface PageHeaderProps {
  section: string
  title: string
  subtitle: string
  actions?: ReactNode
}

export function PageHeader({ section, title, subtitle, actions }: PageHeaderProps) {
  const { t } = useTranslation()
  return (
    <Stack
      direction={{ xs: 'column', sm: 'row' }}
      spacing={2}
      sx={{ justifyContent: 'space-between', alignItems: { sm: 'flex-end' } }}
    >
      <Box>
        <Typography variant="overline" color="primary.main" sx={{ fontWeight: 700 }}>
          {t('app.name')} · {section}
        </Typography>
        <Typography variant="h4" component="h1" sx={{ mt: 0.5 }}>
          {title}
        </Typography>
        <Typography color="text.secondary" sx={{ mt: 0.5 }}>
          {subtitle}
        </Typography>
      </Box>
      {actions}
    </Stack>
  )
}
