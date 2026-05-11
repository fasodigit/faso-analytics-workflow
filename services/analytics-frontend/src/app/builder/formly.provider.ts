import { EnvironmentProviders, importProvidersFrom } from '@angular/core';
import { FormlyModule } from '@ngx-formly/core';
import { FD_FORMLY_TYPES } from './formly-field-types';

export function provideFormly(): EnvironmentProviders {
  return importProvidersFrom(
    FormlyModule.forRoot({
      types: FD_FORMLY_TYPES,
      validationMessages: [
        { name: 'required', message: 'Champ requis' },
      ],
    }),
  );
}
