# HbA1cApi

All URIs are relative to *http://localhost:8080/api/v1*

|Method | HTTP request | Description|
|------------- | ------------- | -------------|
|[**createHba1cEntry**](#createhba1centry) | **POST** /users/{userId}/hba1c | Create a manual lab HbA1c entry|
|[**listHba1cEntries**](#listhba1centries) | **GET** /users/{userId}/hba1c | List HbA1c entries for a user|

# **createHba1cEntry**
> HbA1cEntryResponse createHba1cEntry(createHba1cEntryRequest)

Records a manually entered lab HbA1c result or a CGM-estimated value for the given user.

### Example

```typescript
import {
    HbA1cApi,
    Configuration,
    CreateHba1cEntryRequest
} from './api';

const configuration = new Configuration();
const apiInstance = new HbA1cApi(configuration);

let userId: string; //The target user\'s UUID (default to undefined)
let createHba1cEntryRequest: CreateHba1cEntryRequest; //

const { status, data } = await apiInstance.createHba1cEntry(
    userId,
    createHba1cEntryRequest
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **createHba1cEntryRequest** | **CreateHba1cEntryRequest**|  | |
| **userId** | [**string**] | The target user\&#39;s UUID | defaults to undefined|


### Return type

**HbA1cEntryResponse**

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
|**201** | HbA1c entry created |  -  |
|**400** | Bad Request |  -  |
|**401** | Unauthorized |  -  |
|**403** | Forbidden |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **listHba1cEntries**
> Array<HbA1cEntryResponse> listHba1cEntries()

Returns all lab-measured or CGM-estimated HbA1c entries for the given user, optionally filtered by date range.

### Example

```typescript
import {
    HbA1cApi,
    Configuration
} from './api';

const configuration = new Configuration();
const apiInstance = new HbA1cApi(configuration);

let userId: string; //The target user\'s UUID (default to undefined)
let from: string; //Filter entries on or after this timestamp (ISO-8601) (optional) (default to undefined)
let to: string; //Filter entries on or before this timestamp (ISO-8601) (optional) (default to undefined)

const { status, data } = await apiInstance.listHba1cEntries(
    userId,
    from,
    to
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **userId** | [**string**] | The target user\&#39;s UUID | defaults to undefined|
| **from** | [**string**] | Filter entries on or after this timestamp (ISO-8601) | (optional) defaults to undefined|
| **to** | [**string**] | Filter entries on or before this timestamp (ISO-8601) | (optional) defaults to undefined|


### Return type

**Array<HbA1cEntryResponse>**

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
|**200** | List of HbA1c entries |  -  |
|**401** | Unauthorized |  -  |
|**403** | Forbidden |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

