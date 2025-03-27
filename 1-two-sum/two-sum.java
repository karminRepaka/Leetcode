class Solution {
    public int[] twoSum(int[] nums, int target) {
        HashMap<Integer, Integer> helper = new HashMap<>();
        for(int i =0;i<nums.length;i++)
        {
            int remainder  = target - nums[i];
            if(helper.containsKey(remainder))
            {
                return new int[]{i,helper.get(remainder)};
                
            }
            else
            {
                helper.put(nums[i], i);

            }


        }
        return new int[]{-1,-1};
        
        
    }
}