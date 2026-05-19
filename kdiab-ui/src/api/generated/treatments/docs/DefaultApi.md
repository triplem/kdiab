# DefaultApi

All URIs are relative to *http://localhost:8080/api/v1*

|Method | HTTP request | Description|
|------------- | ------------- | -------------|
|[**archiveTreatments**](#archivetreatments) | **POST** /users/{userId}/treatments/archive | Archive treatments (soft-delete)|
|[**createTreatment**](#createtreatment) | **POST** /users/{userId}/treatments | Create a new treatment record|
|[**deleteTreatments**](#deletetreatments) | **POST** /users/{userId}/treatments/delete | Permanently delete treatments|
|[**listTreatments**](#listtreatments) | **GET** /users/{userId}/treatments | List all treatments for a user|
|[**unarchiveTreatments**](#unarchivetreatments) | **POST** /users/{userId}/treatments/unarchive | Unarchive treatments|
|[**updateTreatment**](#updatetreatment) | **PUT** /users/{userId}/treatments/{treatmentId} | Update a treatment|

# **archiveTreatments**
> archiveTreatments(bulkTreatmentRequest)

Mark treatments as ARCHIVED. Available to all authorized users (patients for their own, doctors for assigned patients, admins for all).

### Example

```typescript
import {
    DefaultApi,
    Configuration,
    BulkTreatmentRequest
} from './api';

const configuration = new Configuration();
const apiInstance = new DefaultApi(configuration);

let userId: string; // (default to undefined)
let bulkTreatmentRequest: BulkTreatmentRequest; //

const { status, data } = await apiInstance.archiveTreatments(
    userId,
    bulkTreatmentRequest
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **bulkTreatmentRequest** | **BulkTreatmentRequest**|  | |
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
|**200** | Treatments archived successfully |  -  |
|**401** | Unauthorized |  -  |
|**403** | Forbidden |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **createTreatment**
> TreatmentResponse createTreatment(createTreatmentRequest)

Records a new treatment event (bolus, carbs, basal, etc.) for the given user with ACTIVE status.

### Example

```typescript
import {
    DefaultApi,
    Configuration,
    CreateTreatmentRequest
} from './api';

const configuration = new Configuration();
const apiInstance = new DefaultApi(configuration);

let userId: string; // (default to undefined)
let createTreatmentRequest: CreateTreatmentRequest; //

const { status, data } = await apiInstance.createTreatment(
    userId,
    createTreatmentRequest
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **createTreatmentRequest** | **CreateTreatmentRequest**|  | |
| **userId** | [**string**] |  | defaults to undefined|


### Return type

**TreatmentResponse**

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
|**201** | Treatment created successfully |  * Location - URL of the created resource <br>  |
|**400** | Bad Request |  -  |
|**401** | Unauthorized |  -  |
|**403** | Forbidden |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **deleteTreatments**
> deleteTreatments(bulkTreatmentRequest)

Patients can delete their own treatments. Doctors can delete for their assigned patients. Admins can delete for all users.

### Example

```typescript
import {
    DefaultApi,
    Configuration,
    BulkTreatmentRequest
} from './api';

const configuration = new Configuration();
const apiInstance = new DefaultApi(configuration);

let userId: string; // (default to undefined)
let bulkTreatmentRequest: BulkTreatmentRequest; //

const { status, data } = await apiInstance.deleteTreatments(
    userId,
    bulkTreatmentRequest
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **bulkTreatmentRequest** | **BulkTreatmentRequest**|  | |
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
|**200** | Treatments deleted successfully |  -  |
|**401** | Unauthorized |  -  |
|**403** | Forbidden |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **listTreatments**
> PagedTreatmentResponse listTreatments()

Returns a paginated list of treatment events for the given user, optionally filtered by type, status, and date range.

### Example

```typescript
import {
    DefaultApi,
    Configuration
} from './api';

const configuration = new Configuration();
const apiInstance = new DefaultApi(configuration);

let userId: string; // (default to undefined)
let type: TreatmentType; //Filter by treatment type (optional) (default to undefined)
let from: string; //Filter treatments on or after this timestamp (ISO-8601) (optional) (default to undefined)
let to: string; //Filter treatments on or before this timestamp (ISO-8601) (optional) (default to undefined)
let status: 'ACTIVE' | 'ARCHIVED'; //Filter by treatment status (default ACTIVE) (optional) (default to 'ACTIVE')
let page: number; //Zero-based page number (default 0) (optional) (default to 0)
let size: number; //Page size (default 50, max 200) (optional) (default to 50)

const { status, data } = await apiInstance.listTreatments(
    userId,
    type,
    from,
    to,
    status,
    page,
    size
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **userId** | [**string**] |  | defaults to undefined|
| **type** | **TreatmentType** | Filter by treatment type | (optional) defaults to undefined|
| **from** | [**string**] | Filter treatments on or after this timestamp (ISO-8601) | (optional) defaults to undefined|
| **to** | [**string**] | Filter treatments on or before this timestamp (ISO-8601) | (optional) defaults to undefined|
| **status** | [**&#39;ACTIVE&#39; | &#39;ARCHIVED&#39;**]**Array<&#39;ACTIVE&#39; &#124; &#39;ARCHIVED&#39;>** | Filter by treatment status (default ACTIVE) | (optional) defaults to 'ACTIVE'|
| **page** | [**number**] | Zero-based page number (default 0) | (optional) defaults to 0|
| **size** | [**number**] | Page size (default 50, max 200) | (optional) defaults to 50|


### Return type

**PagedTreatmentResponse**

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
|**200** | Paginated list of treatments |  -  |
|**401** | Unauthorized |  -  |
|**403** | Forbidden |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **unarchiveTreatments**
> unarchiveTreatments(bulkTreatmentRequest)

Restore archived treatments to ACTIVE status.

### Example

```typescript
import {
    DefaultApi,
    Configuration,
    BulkTreatmentRequest
} from './api';

const configuration = new Configuration();
const apiInstance = new DefaultApi(configuration);

let userId: string; // (default to undefined)
let bulkTreatmentRequest: BulkTreatmentRequest; //

const { status, data } = await apiInstance.unarchiveTreatments(
    userId,
    bulkTreatmentRequest
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **bulkTreatmentRequest** | **BulkTreatmentRequest**|  | |
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
|**200** | Treatments unarchived successfully |  -  |
|**401** | Unauthorized |  -  |
|**403** | Forbidden |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **updateTreatment**
> TreatmentResponse updateTreatment(updateTreatmentRequest)

Updates the timestamp, data payload, and optional notes of an existing treatment record. The treatment type cannot be changed.

### Example

```typescript
import {
    DefaultApi,
    Configuration,
    UpdateTreatmentRequest
} from './api';

const configuration = new Configuration();
const apiInstance = new DefaultApi(configuration);

let userId: string; // (default to undefined)
let treatmentId: string; // (default to undefined)
let updateTreatmentRequest: UpdateTreatmentRequest; //

const { status, data } = await apiInstance.updateTreatment(
    userId,
    treatmentId,
    updateTreatmentRequest
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **updateTreatmentRequest** | **UpdateTreatmentRequest**|  | |
| **userId** | [**string**] |  | defaults to undefined|
| **treatmentId** | [**string**] |  | defaults to undefined|


### Return type

**TreatmentResponse**

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
|**200** | Treatment updated |  -  |
|**400** | Bad Request |  -  |
|**401** | Unauthorized |  -  |
|**403** | Forbidden |  -  |
|**404** | Not Found |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

