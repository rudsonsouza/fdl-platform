# Cobertura dos cenários 400

Cada cenário `400` das specs em `openspec/changes/add-order-tracking-system/specs` e o teste que o exercita. Os testes `*ValidationTest` são de fatia web (MockMvc, serviço simulado) e verificam também que o serviço **não** é chamado; `OrdersApiIT.invalidPayloadsPersistNothing` prova, com SQLite real, que nada é gravado.

Pacotes de teste: `auth/api/AuthControllerValidationTest`, `order/api/CreateOrderValidationTest`, `order/api/ListAndGetOrderValidationTest`, `order/api/UpdateStatusValidationTest`, `shared/error/ProblemDetailsTest`.

## user-auth

| Cenário (spec) | Teste |
| --- | --- |
| Cadastro: corpo vazio | `AuthControllerValidationTest.emptyBodyListsNameEmailAndPassword` |
| Cadastro: e-mail com formato inválido (e acima de 254) | `invalidEmailIsRejected`, `emailLongerThan254CharactersIsRejected` |
| Cadastro: senha fraca ou fora do tamanho | `weakOrShortPasswordIsRejected`, `passwordWith73CharactersIsRejected`, `passwordUnder72CharactersButOver72BytesIsRejected` |
| Cadastro: nome em branco ou longo demais | `blankOrTooShortNameIsRejected`, `nameWith101CharactersIsRejected` |
| Cadastro: campo desconhecido | `unknownFieldIsMalformedRequest` |
| Cadastro: JSON malformado | `malformedJsonIsMalformedRequest` |
| Login: senha ausente | `loginWithoutPasswordNamesThePasswordField`, `loginWithEmptyPasswordIsRejected` |
| Login: e-mail inválido | `loginWithInvalidEmailNamesTheEmailField` |
| Login: JSON malformado | `loginWithMalformedJsonIsMalformedRequest` |

## order-management

| Cenário (spec) | Teste |
| --- | --- |
| Corpo vazio ou JSON malformado | `CreateOrderValidationTest.emptyBodyListsCustomerItemsAndAddress`, `malformedJsonIsMalformedRequest` |
| Campos somente-servidor e desconhecidos | `serverOnlyAndUnknownFieldsAreRejected`, `unknownFieldInsideAnItemIsRejected` |
| Cliente inválido | `missingCustomer`, `customerNameEmptyBlankOrTooShort`, `customerNameLongerThan100`, `customerPhoneMustBe10Or11Digits` |
| Lista de itens inválida | `missingItems`, `emptyItems`, `moreThan50Items`, `nullItemInTheList` |
| Item inválido | `itemNameEmptyOrBlank`, `itemWithoutName`, `itemNameLongerThan120`, `itemQuantityOutOfRange`, `itemWithoutQuantity`, `itemQuantityDecimalOrNonNumeric`, `itemUnitPriceOutOfRange`, `itemWithoutUnitPrice`, `errorsPointAtTheOffendingItemIndex` |
| Endereço inválido | `missingAddress`, `requiredAddressTextFieldsCannotBeEmptyOrBlank`, `addressTextFieldsAbove120CharactersAreRejected`, `stateMustBeTwoUppercaseLetters`, `zipCodeMustHaveExactly8Digits` |
| Parâmetros de listagem inválidos | `ListAndGetOrderValidationTest.invalidStatusFilterIsInvalidParameter`, `outOfRangePagingIsAValidationError`, `nonNumericPagingIsInvalidParameter`, `sortOutsideTheAllowlistIsAValidationError` |
| ID em formato inválido | `getWithAnIdThatIsNotAUuidIsInvalidParameter`, `OrdersApiIT.unknownOrderIs404AndBadIdIs400` |
| Erro de validação padronizado | `ProblemDetailsTest.everyErrorCarriesTypeTitleStatusDetailInstanceCodeAndTraceId`, `validationErrorsPointToTheNestedField` |
| Nada é persistido | `OrdersApiIT.invalidPayloadsPersistNothing` |

## order-status-lifecycle

| Cenário (spec) | Teste |
| --- | --- |
| Status ausente ou nulo | `UpdateStatusValidationTest.missingOrNullStatusNamesTheStatusField` |
| Status inexistente, grafia diferente, não textual | `unknownOrNonTextualStatusIsMalformedRequest` |
| JSON malformado / campo desconhecido | `malformedJsonIsMalformedRequest`, `unknownFieldIsRejected` |
| ID inválido | `idThatIsNotAUuidIsInvalidParameter` |
| Pedido não muda quando rejeitado | `OrdersApiIT.rejectedTransitionsDoNotChangeTheOrderOrItsHistory` |

## order-tracking-web

| Cenário (spec) | Teste |
| --- | --- |
| `400` do servidor liga ao campo (`deliveryAddress.zipCode`, `items[0].unitPriceCents`) | `NewOrderPage.test.tsx` "mapeia erros 400 do servidor para os campos" |
| Mesmas regras no cliente | `schemas.test.ts` (criação) e `features/auth/schemas.test.ts` |
