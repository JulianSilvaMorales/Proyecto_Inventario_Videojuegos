import { useState } from 'react'
import { Alert, Button, Stack } from '@mui/material'
import FileDownloadRoundedIcon from '@mui/icons-material/FileDownloadRounded'
import { useTranslation } from 'react-i18next'
import { apiDownload } from '../../api/apiClient'
import { apiErrorMessage } from '../../api/errors'

interface ExportButtonProps {
  path: string
  fileName: string
  disabled?: boolean
}

export function ExportButton({ path, fileName, disabled }: ExportButtonProps) {
  const { t } = useTranslation()
  const [exporting, setExporting] = useState(false)
  const [error, setError] = useState('')

  async function exportCsv() {
    setError('')
    setExporting(true)
    try {
      await apiDownload(path, fileName)
    } catch (reason) {
      setError(apiErrorMessage(reason, t))
    } finally {
      setExporting(false)
    }
  }

  return (
    <Stack spacing={1} sx={{ alignItems: 'flex-end' }}>
      <Button
        variant="outlined"
        startIcon={<FileDownloadRoundedIcon />}
        disabled={disabled || exporting}
        onClick={() => void exportCsv()}
      >
        {exporting ? t('reports.exporting') : t('reports.exportCsv')}
      </Button>
      {error && <Alert severity="error">{error}</Alert>}
    </Stack>
  )
}
