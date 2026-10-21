import { Button, Stack } from '@mui/material'
import { useTranslation } from 'react-i18next'
import { accessibleCrudEntities, type CrudEntityMetadata } from '../crud/metadata'

interface CrudEntityLinksProps {
  module: string
  permissions: readonly string[]
  onSelectEntity: (entity: CrudEntityMetadata) => void
}

/** Accesos a los CRUD genéricos (E6.2) de un módulo que el usuario puede consultar. */
export function CrudEntityLinks({ module, permissions, onSelectEntity }: CrudEntityLinksProps) {
  const { i18n } = useTranslation()
  const english = i18n.resolvedLanguage?.toLowerCase().startsWith('en')
  const entities = accessibleCrudEntities(module, permissions)
  if (entities.length === 0) {
    return null
  }
  return (
    <Stack direction="row" spacing={1.25} useFlexGap sx={{ flexWrap: 'wrap' }}>
      {entities.map((entity) => (
        <Button key={entity.nombre} variant="outlined" onClick={() => onSelectEntity(entity)}>
          {english ? entity.etiquetas.en : entity.etiquetas.es}
        </Button>
      ))}
    </Stack>
  )
}
