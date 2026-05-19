# DefaultApi

All URIs are relative to *http://localhost:8080/api/v1*

|Method | HTTP request | Description|
|------------- | ------------- | -------------|
|[**archiveMeasures**](#archivemeasures) | **POST** /users/{userId}/measures/archive | Archive multiple measures|
|[**createMeasure**](#createmeasure) | **POST** /users/{userId}/measures | Create a new measure|
|[**deleteMeasures**](#deletemeasures) | **POST** /users/{userId}/measures/delete | Permanently delete measures|
|[**listMeasures**](#listmeasures) | **GET** /users/{userId}/measures | List active measures for a user (paginated)|
|[**unarchiveMeasures**](#unarchivemeasures) | **POST** /users/{userId}/measures/unarchive | Unarchive measures|
|[**updateMeasure**](#updatemeasure) | **PUT** /users/{userId}/measures/{measureId} | Update a measure|

# **archiveMeasures**
> archiveMeasures(bulkMeasureRequest)

Patients can archive their own measures. Doctors can archive for their allowed patients.

### Example

```typescript
import {
    DefaultApi,
    Configuration,
    BulkMeasureRequest
} from './api';

const configuration = new Configuration();
const apiInstance = new DefaultApi(configuration);

let userId: string; // (default to undefined)
let bulkMeasureRequest: BulkMeasureRequest; //

const { status, data } = await apiInstance.archiveMeasures(
    userId,
    bulkMeasureRequest
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **bulkMeasureRequest** | **BulkMeasureRequest**|  | |
| **userId** | [**string**] |  | defaults to undefined|


### Return type

void (empty response body)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
|**200** | Measures archived successfully |  -  |
|**401** | Unauthorized |  -  |
|**403** | Forbidden |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **createMeasure**
> MeasureResponse createMeasure(createMeasureRequest)

Records a new health measurement for the user with ACTIVE status.

### Example

```typescript
import {
    DefaultApi,
    Configuration,
    CreateMeasureRequest
} from './api';

const configuration = new Configuration();
const apiInstance = new DefaultApi(configuration);

let userId: string; // (default to undefined)
let createMeasureRequest: CreateMeasureRequest; //

const { status, data } = await apiInstance.createMeasure(
    userId,
    createMeasureRequest
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **createMeasureRequest** | **CreateMeasureRequest**|  | |
| **userId** | [**string**] |  | defaults to undefined|


### Return type

**MeasureResponse**

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
|**201** | Measure created successfully |  * Location - URL of the created resource <br>  |
|**400** | Bad Request |  -  |
|**401** | Unauthorized |  -  |
|**403** | Forbidden |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **deleteMeasures**
> deleteMeasures(bulkMeasureRequest)

Only doctors and admins can permanently delete measures.

### Example

```typescript
import {
    DefaultApi,
    Configuration,
    BulkMeasureRequest
} from './api';

const configuration = new Configuration();
const apiInstance = new DefaultApi(configuration);

let userId: string; // (default to undefined)
let bulkMeasureRequest: BulkMeasureRequest; //

const { status, data } = await apiInstance.deleteMeasures(
    userId,
    bulkMeasureRequest
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **bulkMeasureRequest** | **BulkMeasureRequest**|  | |
| **userId** | [**string**] |  | defaults to undefined|


### Return type

void (empty response body)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
|**200** | Measures deleted successfully |  -  |
|**401** | Unauthorized |  -  |
|**403** | Forbidden |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **listMeasures**
> PagedMeasureResponse listMeasures()

Returns a paginated list of health measurements for the given user, filtered by status (default ACTIVE).

### Example

```typescript
import {
    DefaultApi,
    Configuration
} from './api';

const configuration = new Configuration();
const apiInstance = new DefaultApi(configuration);

let userId: string; // (default to undefined)
let page: number; // (optional) (default to 0)
let size: number; // (optional) (default to 50)
let from: string; //Filter measures on or after this timestamp (ISO-8601) (optional) (default to undefined)
let to: string; //Filter measures on or before this timestamp (ISO-8601) (optional) (default to undefined)
let status: 'ACTIVE' | 'ARCHIVED'; //Filter by measure status (default ACTIVE) (optional) (default to 'ACTIVE')

const { status, data } = await apiInstance.listMeasures(
    userId,
    page,
    size,
    from,
    to,
    status
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **userId** | [**string**] |  | defaults to undefined|
| **page** | [**number**] |  | (optional) defaults to 0|
| **size** | [**number**] |  | (optional) defaults to 50|
| **from** | [**string**] | Filter measures on or after this timestamp (ISO-8601) | (optional) defaults to undefined|
| **to** | [**string**] | Filter measures on or before this timestamp (ISO-8601) | (optional) defaults to undefined|
| **status** | [**&#39;ACTIVE&#39; | &#39;ARCHIVED&#39;**]**Array<&#39;ACTIVE&#39; &#124; &#39;ARCHIVED&#39;>** | Filter by measure status (default ACTIVE) | (optional) defaults to 'ACTIVE'|


### Return type

**PagedMeasureResponse**

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
|**200** | Paginated list of active measures |  -  |
|**401** | Unauthorized |  -  |
|**403** | Forbidden |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **unarchiveMeasures**
> unarchiveMeasures(bulkMeasureRequest)

Restore archived measures to ACTIVE status.

### Example

```typescript
import {
    DefaultApi,
    Configuration,
    BulkMeasureRequest
} from './api';

const configuration = new Configuration();
const apiInstance = new DefaultApi(configuration);

let userId: string; // (default to undefined)
let bulkMeasureRequest: BulkMeasureRequest; //

const { status, data } = await apiInstance.unarchiveMeasures(
    userId,
    bulkMeasureRequest
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **bulkMeasureRequest** | **BulkMeasureRequest**|  | |
| **userId** | [**string**] |  | defaults to undefined|


### Return type

void (empty response body)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
|**200** | Measures unarchived successfully |  -  |
|**401** | Unauthorized |  -  |
|**403** | Forbidden |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **updateMeasure**
> MeasureResponse updateMeasure(updateMeasureRequest)

Updates the timestamp and data payload of an existing measure. The measure type cannot be changed.

### Example

```typescript
import {
    DefaultApi,
    Configuration,
    UpdateMeasureRequest
} from './api';

const configuration = new Configuration();
const apiInstance = new DefaultApi(configuration);

let userId: string; // (default to undefined)
let measureId: string; // (default to undefined)
let updateMeasureRequest: UpdateMeasureRequest; //

const { status, data } = await apiInstance.updateMeasure(
    userId,
    measureId,
    updateMeasureRequest
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **updateMeasureRequest** | **UpdateMeasureRequest**|  | |
| **userId** | [**string**] |  | defaults to undefined|
| **measureId** | [**string**] |  | defaults to undefined|


### Return type

**MeasureResponse**

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
|**200** | Measure updated |  -  |
|**400** | Bad Request |  -  |
|**401** | Unauthorized |  -  |
|**403** | Forbidden |  -  |
|**404** | Not Found |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

