# TirBreakdown


## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**veryLowCount** | **number** | Readings below 54 mg/dL (Level 2 hypoglycaemia — severe). International Consensus on TIR (Battelino et al. 2019).  | [default to undefined]
**belowCount** | **number** | Readings 54–70 mg/dL (Level 1 hypoglycaemia) | [default to undefined]
**inRangeCount** | **number** | Readings 70–180 mg/dL (target range) | [default to undefined]
**aboveCount** | **number** | Readings 180–250 mg/dL (elevated) | [default to undefined]
**highCount** | **number** | Readings above 250 mg/dL (significantly elevated) | [default to undefined]
**totalCount** | **number** |  | [default to undefined]

## Example

```typescript
import { TirBreakdown } from './api';

const instance: TirBreakdown = {
    veryLowCount,
    belowCount,
    inRangeCount,
    aboveCount,
    highCount,
    totalCount,
};
```

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)
