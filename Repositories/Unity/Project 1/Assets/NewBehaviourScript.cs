using System.Collections;
using System.Collections.Generic;
using UnityEngine;

public class NewBehaviourScript : MonoBehaviour
{
    // Start is called before the first frame update
    void Start()
    {

    }

    // Update is called once per frame
    void Update()
    {
        if (Input.GetButton("Jump"))
            GetComponent<Rigidbody>().velocity = new Vector3(0, 2, 0);
        GetComponent<Transform>().Translate(Vector3.forward * Input.GetAxis("Vertical"));
        transform.Translate(Vector3.right * Input.GetAxis("Horizontal"));

        if (Physics.Raycast(transform.position, Vector3.forward, out RaycastHit hitInfo))
        {
            Debug.DrawRay(transform.position, Vector3.forward, Color.blue, 2.0f); // 플레이 화면에서 Gizmo를 활성화하면 광선이 보인다
            Debug.Log($"Ray hit : {hitInfo.collider.gameObject.name}");
            Instantiate(null);
        }
    }

    private void OnCollisionEnter(Collision collision)
    {
        Debug.Log($"충돌 : {name} vs {collision.gameObject.name}");
    }

    private void OnGUI()
    {
        if (GUI.RepeatButton(new Rect(80, 30, 60, 40), " ^ "))
        {
            transform.Translate(Vector3.up);
        }
        GUILayout.Label("위로 이동");
    }


}
